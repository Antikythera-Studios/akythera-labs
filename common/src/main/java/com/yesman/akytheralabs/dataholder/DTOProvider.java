package com.yesman.akytheralabs.dataholder;

import java.util.Map;

/// Provides DTO in [java.util.Map] shape to hand over parameters
/// to replace string literals in .ftl source code templates.
public interface DTOProvider {
    Map<String, Object> getDTO();
}
