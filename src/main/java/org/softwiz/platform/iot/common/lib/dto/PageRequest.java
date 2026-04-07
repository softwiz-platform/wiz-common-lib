package org.softwiz.platform.iot.common.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {

    private Integer page = 1;

    private Integer pageSize = 20;

    private Integer offset;

    // "page": 1 형태로 숫자만 넘어올 때 처리
    @JsonCreator
    public PageRequest(Integer page) {
        this.page = (page != null && page >= 1) ? page : 1;
        this.pageSize = 20;
        this.offset = (this.page - 1) * this.pageSize;
    }

    public Integer getPage() {
        return (this.page == null || this.page < 1) ? 1 : this.page;
    }

    public Integer getPageSize() {
        return (this.pageSize == null || this.pageSize < 1) ? 20 : this.pageSize;
    }

    public Integer getOffset() {
        return (getPage() - 1) * getPageSize();
    }

    // init()은 하위 호환성 유지
    public void init() {
        this.page = getPage();
        this.pageSize = getPageSize();
        this.offset = getOffset();
    }
}