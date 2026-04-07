package org.softwiz.platform.iot.common.lib.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PagedApiResponse<T> extends ApiResponse<T> {

    private PageInfo pageInfo;

    public static <T> PagedApiResponse<T> success(T data, PageInfo pageInfo) {
        PagedApiResponse<T> response = new PagedApiResponse<>();
        response.setData(data);
        response.setPageInfo(pageInfo);
        return response;
    }

    public static <T> PagedApiResponse<T> success(T data, String message, PageInfo pageInfo) {
        PagedApiResponse<T> response = new PagedApiResponse<>();
        response.setData(data);
        response.setMessage(message);
        response.setPageInfo(pageInfo);
        return response;
    }
}