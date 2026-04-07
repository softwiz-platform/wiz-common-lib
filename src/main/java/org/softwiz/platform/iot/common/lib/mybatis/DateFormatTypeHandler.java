package org.softwiz.platform.iot.common.lib.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MyBatis TypeHandler: TIMESTAMP ↔ String (yyyy-MM-dd HH:mm:ss)
 * 
 * 기능:
 * - DB 조회 시: TIMESTAMP → "yyyy-MM-dd HH:mm:ss" String
 * - DB 저장 시: "yyyy-MM-dd HH:mm:ss" String → TIMESTAMP
 * 
 * 사용법:
 * - Domain 필드를 String으로 선언
 * - MyBatis가 자동으로 변환 처리
 * - SQL에서 DATE_FORMAT 불필요
 */
@MappedJdbcTypes(JdbcType.TIMESTAMP)
@MappedTypes(String.class)
public class DateFormatTypeHandler extends BaseTypeHandler<String> {
    
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * String → TIMESTAMP (INSERT/UPDATE 시)
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        LocalDateTime dt = LocalDateTime.parse(parameter, FORMATTER);
        ps.setTimestamp(i, Timestamp.valueOf(dt));
    }

    /**
     * TIMESTAMP → String (SELECT 시 - 컬럼명으로 조회)
     */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime().format(FORMATTER) : null;
    }

    /**
     * TIMESTAMP → String (SELECT 시 - 인덱스로 조회)
     */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnIndex);
        return timestamp != null ? timestamp.toLocalDateTime().format(FORMATTER) : null;
    }

    /**
     * TIMESTAMP → String (CallableStatement - Stored Procedure 호출 시)
     */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Timestamp timestamp = cs.getTimestamp(columnIndex);
        return timestamp != null ? timestamp.toLocalDateTime().format(FORMATTER) : null;
    }
}