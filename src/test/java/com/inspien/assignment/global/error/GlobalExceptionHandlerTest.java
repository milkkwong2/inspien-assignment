package com.inspien.assignment.global.error;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inspien.assignment.global.web.ApiResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
@ActiveProfiles("local")
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void successWrapsDataWithTraceId() throws Exception {
		this.mvc.perform(get("/test/ok"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.traceId").isString())
			.andExpect(jsonPath("$.data.value").value("hello"))
			.andExpect(jsonPath("$.page").doesNotExist())
			.andExpect(jsonPath("$.success").doesNotExist());
	}

	@Test
	void retryableErrorIsProblemDetailWithRetryAfter() throws Exception {
		this.mvc.perform(get("/test/unavailable"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string("Retry-After", "30"))
			.andExpect(jsonPath("$.status").value(503))
			.andExpect(jsonPath("$.detail").value("쇼핑몰 DB에 저장하지 못했습니다."))
			.andExpect(jsonPath("$.instance").value("/test/unavailable"))
			.andExpect(jsonPath("$.code").value("ORDER_DB_UNAVAILABLE"))
			.andExpect(jsonPath("$.retryable").value(true))
			.andExpect(jsonPath("$.messageId").value("0HZ8K3V2M1Q7R"))
			.andExpect(jsonPath("$.traceId").isString());
	}

	@Test
	void nonRetryableErrorHasNoRetryAfterOrMessageId() throws Exception {
		this.mvc.perform(get("/test/bad"))
			.andExpect(status().isBadRequest())
			.andExpect(header().doesNotExist("Retry-After"))
			.andExpect(jsonPath("$.code").value("XML_PARSE_ERROR"))
			.andExpect(jsonPath("$.retryable").value(false))
			.andExpect(jsonPath("$.messageId").doesNotExist());
	}

	@Test
	void unexpectedExceptionDoesNotLeakInternals() throws Exception {
		this.mvc.perform(get("/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.detail").value("서버 오류가 발생했습니다. 연계 담당자에게 문의해 주세요."));
	}

	@Test
	void springMvcErrorsAlsoCarryTraceId() throws Exception {
		this.mvc.perform(post("/test/ok"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.traceId").isString());
	}

	@RestController
	static class TestController {

		@GetMapping("/test/ok")
		ApiResponse<Value> ok() {
			return ApiResponse.of(new Value("hello"));
		}

		@GetMapping("/test/unavailable")
		void unavailable() {
			throw new ApiException(ErrorCode.ORDER_DB_UNAVAILABLE, "쇼핑몰 DB에 저장하지 못했습니다.", "0HZ8K3V2M1Q7R",
					new IllegalStateException("ORA-12541 at jdbc:oracle:thin:@secret-host"));
		}

		@GetMapping("/test/bad")
		void bad() {
			throw new ApiException(ErrorCode.XML_PARSE_ERROR, "본문을 읽을 수 없습니다.");
		}

		@GetMapping("/test/boom")
		void boom() {
			throw new IllegalStateException("internal detail jdbc:oracle:thin:@secret-host");
		}

		record Value(String value) {
		}

	}

}
