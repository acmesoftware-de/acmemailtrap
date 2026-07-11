package de.acmesoftware.mailtrap.imap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImapSessionParseTest {

    @Test
    void tokenizeHonoursQuotesAndParens() {
        List<String> t = ImapSession.tokenize("a1 LOGIN \"bob@x.test\" \"pw\"");
        assertThat(t).containsExactly("a1", "LOGIN", "bob@x.test", "pw");

        List<String> f = ImapSession.tokenize("a2 FETCH 1:3 (FLAGS BODY.PEEK[])");
        assertThat(f).containsExactly("a2", "FETCH", "1:3", "(FLAGS BODY.PEEK[])");
    }

    @Test
    void parseSeqSetExpandsRangesAndStar() {
        assertThat(ImapSession.parseSeqSet("1,3:5", 10)).containsExactly(1, 3, 4, 5);
        assertThat(ImapSession.parseSeqSet("2:*", 4)).containsExactly(2, 3, 4);
        assertThat(ImapSession.parseSeqSet("*", 3)).containsExactly(3);
        assertThat(ImapSession.parseSeqSet("1:3", 0)).isEmpty();
    }

    @Test
    void parseFetchItemsKeepsBodySectionsWhole() {
        assertThat(ImapSession.parseFetchItems("(FLAGS BODY.PEEK[HEADER] UID)"))
                .containsExactly("FLAGS", "BODY.PEEK[HEADER]", "UID");
        assertThat(ImapSession.parseFetchItems("BODY[]")).containsExactly("BODY[]");
        assertThat(ImapSession.parseFetchItems("RFC822.SIZE")).containsExactly("RFC822.SIZE");
    }

    @Test
    void parseFetchItemsExpandsMacros() {
        assertThat(ImapSession.parseFetchItems("FAST"))
                .containsExactly("FLAGS", "INTERNALDATE", "RFC822.SIZE");
        assertThat(ImapSession.parseFetchItems("ALL"))
                .contains("ENVELOPE");
    }
}
