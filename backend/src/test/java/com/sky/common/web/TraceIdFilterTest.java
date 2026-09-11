package com.sky.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesTraceIdWhenHeaderMissingAndEchoesItBack() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/shop/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String header = response.getHeader(TraceIdFilter.HEADER);
        assertThat(header).isNotNull().hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void reusesIncomingTraceIdSoCallersCanCorrelate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(TraceIdFilter.HEADER, "0f3a1c2b4d5e6f70");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("0f3a1c2b4d5e6f70");
    }

    @Test
    void exposesTraceIdToLogsDuringTheRequestAndRemovesItAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> duringRequest.set(TraceIdFilter.currentTraceId());

        assertThat(TraceIdFilter.currentTraceId()).isNull();
        filter.doFilter(request, response, chain);

        assertThat(duringRequest.get()).isNotNull().isEqualTo(response.getHeader(TraceIdFilter.HEADER));
        // 线程池会复用线程,请求结束后必须清掉 MDC,否则 traceId 会串到下一个请求
        assertThat(TraceIdFilter.currentTraceId()).isNull();
    }
}
