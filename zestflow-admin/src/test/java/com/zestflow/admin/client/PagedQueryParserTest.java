package com.zestflow.admin.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PagedQueryParserTest {

    @Test
    void parse_defaultsWhenMissing() {
        PagedQueryParser.ParsedPage page = PagedQueryParser.parse(null);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
    }

    @Test
    void parse_readsPageAndSize() {
        PagedQueryParser.ParsedPage page = PagedQueryParser.parse("?keyword=a&page=3&size=25");
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(25);
    }

    @Test
    void forFanOut_resetsToFirstPageWithLargeSize() {
        String fanOut = PagedQueryParser.forFanOut("?page=2&size=10&keyword=x", 500);
        assertThat(fanOut).contains("page=1");
        assertThat(fanOut).contains("size=500");
        assertThat(fanOut).contains("keyword=x");
    }
}
