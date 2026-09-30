package com.grantfit.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper 인터페이스 스캔 위치를 한곳에서 관리한다.
 * 도메인 패키지가 늘어나도 각 Mapper에 @Mapper 를 반복해서 붙이지 않는다.
 */
@Configuration
@MapperScan(basePackages = "com.grantfit", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MyBatisConfig {
}
