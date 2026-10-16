package com.grantfit.program;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 DB 를 조회만 한다. (데이터 변경 없음)
 */
@SpringBootTest
@AutoConfigureMockMvc
class SupportProgramControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 목록_응답_형식() throws Exception {
		mockMvc.perform(get("/api/support-programs").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(5))
				.andExpect(jsonPath("$.totalCount").isNumber())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.items[*].rawContent").doesNotExist());
	}

	@Test
	void 잘못된_페이지_파라미터는_400() throws Exception {
		mockMvc.perform(get("/api/support-programs").param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
		mockMvc.perform(get("/api/support-programs").param("size", "0"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/support-programs").param("size", "101"))
				.andExpect(status().isBadRequest());
	}
}
