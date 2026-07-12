package de.acmesoftware.mailtrap.web;

import de.acmesoftware.mailtrap.search.SearchIndex;
import de.acmesoftware.mailtrap.store.MailStore;
import de.acmesoftware.mailtrap.store.MailboxInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Search across the trap (the top-bar / Cmd-K palette). Messages come from the Lucene
 * index (subject/from/body); mailboxes are matched by address. Empty query -&gt; empty.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchIndex index;
    private final MailStore store;

    public SearchController(SearchIndex index, MailStore store) {
        this.index = index;
        this.store = store;
    }

    public record SearchResults(List<MailboxInfo> mailboxes, List<SearchIndex.Hit> messages) {
    }

    @GetMapping
    public SearchResults search(@RequestParam(name = "q", defaultValue = "") String q,
                                @RequestParam(name = "limit", defaultValue = "20") int limit) {
        String query = q.trim();
        if (query.isEmpty()) {
            return new SearchResults(List.of(), List.of());
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<MailboxInfo> mailboxes = store.listMailboxes().stream()
                .filter(b -> b.address().toLowerCase(Locale.ROOT).contains(needle))
                .limit(8)
                .toList();
        List<SearchIndex.Hit> messages = index.search(query, Math.min(Math.max(limit, 1), 50));
        return new SearchResults(mailboxes, messages);
    }
}
