package com.clothing.app.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Service;

@Service
public class OracleProcedureService {

    private final JdbcTemplate jdbcTemplate;

    public OracleProcedureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void executeProcedure(String procedureName, MapSqlParameterSource params) {
        SimpleJdbcCall call = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName(procedureName);

        if (params == null) {
            call.execute();
            return;
        }

        call.execute(params);
    }

    public void executePackageProcedure(String packageName, String procedureName, MapSqlParameterSource params) {
        SimpleJdbcCall call = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName(packageName)
                .withProcedureName(procedureName);
        call.execute(params);
    }
}
