package com.grantfit.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * db/schema.sql 이 적용된 상태인지 확인한다.
 * schema.sql 은 자동 실행되지 않으므로, 적용을 잊은 경우 이 테스트에서 드러난다.
 */
@SpringBootTest
class SchemaTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void support_program_테이블이_존재하고_필요한_컬럼을_가진다() throws Exception {
		assertThat(columnsOf("support_program")).contains(
				"id", "external_id", "title", "organization", "region", "target",
				"application_start", "application_end", "support_amount",
				"original_url", "raw_content", "created_at", "updated_at");
	}

	@Test
	void ai_analysis_테이블이_존재하고_필요한_컬럼을_가진다() throws Exception {
		assertThat(columnsOf("ai_analysis")).contains(
				"id", "support_program_id", "model", "prompt_version", "status",
				"eligibility_json", "exclusion_json", "required_documents_json",
				"support_amount_json", "uncertain_conditions_json", "evidence_json",
				"preference_json", "summary",
				"error_code", "error_message", "analyzed_at", "created_at", "updated_at");
	}

	@Test
	void ai_analysis_는_support_program_을_참조한다() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData metaData = connection.getMetaData();
			try (ResultSet rs = metaData.getImportedKeys(null, "public", "ai_analysis")) {
				assertThat(rs.next()).as("ai_analysis 의 외래키").isTrue();
				assertThat(rs.getString("PKTABLE_NAME")).isEqualTo("support_program");
				assertThat(rs.getString("FKCOLUMN_NAME")).isEqualTo("support_program_id");
			}
		}
	}

	private List<String> columnsOf(String table) throws Exception {
		List<String> columns = new ArrayList<>();
		try (Connection connection = dataSource.getConnection()) {
			try (ResultSet rs = connection.getMetaData().getColumns(null, "public", table, null)) {
				while (rs.next()) {
					columns.add(rs.getString("COLUMN_NAME"));
				}
			}
		}
		assertThat(columns).as("%s 테이블이 존재해야 한다. db/schema.sql 적용 여부를 확인한다.", table).isNotEmpty();
		return columns;
	}
}
