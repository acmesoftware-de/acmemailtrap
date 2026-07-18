package de.acmesoftware.mailtrap.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Minimal SMTP client for {@code send} — enough to hand a message to the trap's receiver, which
 * accepts all mail unauthenticated by design. No TLS, no AUTH: it talks to a loopback/private trap,
 * never to a real MTA. Delivering over SMTP (rather than the API) is the point — it exercises the
 * real receive path an application would take.
 */
public final class SmtpClient {

    private final String host;
    private final int port;

    public SmtpClient(String endpoint) {
        int colon = endpoint.lastIndexOf(':');
        if (colon <= 0) {
            throw CliError.usage("SMTP endpoint must be host:port, got: " + endpoint);
        }
        this.host = endpoint.substring(0, colon);
        try {
            this.port = Integer.parseInt(endpoint.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw CliError.usage("SMTP port is not a number in: " + endpoint);
        }
    }

    public void send(String from, List<String> recipients, byte[] message) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) Duration.ofSeconds(10).toMillis());
            socket.setSoTimeout((int) Duration.ofSeconds(15).toMillis());
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            expect(in, 220);
            command(out, in, "EHLO acmemailtrap-cli", 250);
            command(out, in, "MAIL FROM:<" + from + ">", 250);
            for (String rcpt : recipients) {
                command(out, in, "RCPT TO:<" + rcpt + ">", 250);
            }
            command(out, in, "DATA", 354);
            out.write(dotStuff(message));
            out.write("\r\n.\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            expect(in, 250);
            command(out, in, "QUIT", 221);
        } catch (IOException e) {
            throw new CliError("SMTP delivery to " + host + ":" + port + " failed: " + e.getMessage());
        }
    }

    private void command(OutputStream out, BufferedReader in, String line, int expected) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        expect(in, expected);
    }

    /** Read one full reply (handles multi-line {@code 250-...} continuations) and check the code. */
    private void expect(BufferedReader in, int code) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) {
                throw new CliError("SMTP connection closed while expecting " + code + ".");
            }
        } while (line.length() >= 4 && line.charAt(3) == '-');
        int got = parseCode(line);
        if (got != code) {
            throw new CliError("SMTP server replied '" + line + "', expected " + code + ".");
        }
    }

    private static int parseCode(String line) {
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (RuntimeException e) {
            throw new CliError("Unparseable SMTP reply: " + line);
        }
    }

    /** RFC 5321 dot-stuffing: a line starting with '.' gets an extra leading '.'. */
    private static byte[] dotStuff(byte[] message) {
        String s = new String(message, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\n", "\r\n");
        StringBuilder sb = new StringBuilder(s.length());
        boolean atLineStart = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (atLineStart && c == '.') {
                sb.append('.');
            }
            sb.append(c);
            atLineStart = c == '\n';
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
