package org.softwiz.platform.iot.common.lib.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageInfo {
    
    // 기본 페이징 정보
    private Integer currentPage;
    private Integer pageSize;
    private Integer totalCount;
    private Integer totalPages;
    
    // 네비게이션 플래그
    private Boolean hasNext;
    private Boolean hasPrev;
    private Boolean isFirst;
    private Boolean isLast;
    
    // 페이지 범위
    private Integer startRow;
    private Integer endRow;
    private Integer firstPage;
    private Integer lastPage;
    
    // 페이지 번호 목록 (UI용)
    private Integer[] pageNumbers;
    
    /**
     * PageRequest와 totalCount로 PageInfo 생성
     */
    public static PageInfo of(PageRequest pageRequest, Integer totalCount) {
        return of(pageRequest, totalCount, 5); // 기본 5개 페이지 번호 표시
    }
    
    /**
     * PageRequest와 totalCount, 표시할 페이지 번호 개수로 PageInfo 생성
     */
    public static PageInfo of(PageRequest pageRequest, Integer totalCount, int displayPageCount) {
        PageInfo pageInfo = new PageInfo();
        
        int currentPage = pageRequest.getPage();
        int pageSize = pageRequest.getPageSize();
        
        pageInfo.setCurrentPage(currentPage);
        pageInfo.setPageSize(pageSize);
        pageInfo.setTotalCount(totalCount);
        
        if (totalCount != null && pageSize > 0) {
            // 전체 페이지 수
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            pageInfo.setTotalPages(totalPages);
            
            // 네비게이션 플래그
            pageInfo.setHasNext(currentPage < totalPages);
            pageInfo.setHasPrev(currentPage > 1);
            pageInfo.setIsFirst(currentPage == 1);
            pageInfo.setIsLast(currentPage >= totalPages);
            
            // 현재 페이지의 시작/끝 row
            int startRow = (currentPage - 1) * pageSize + 1;
            int endRow = Math.min(currentPage * pageSize, totalCount);
            pageInfo.setStartRow(startRow);
            pageInfo.setEndRow(endRow);
            
            // 첫/마지막 페이지
            pageInfo.setFirstPage(1);
            pageInfo.setLastPage(totalPages);
            
            // 페이지 번호 목록 계산
            Integer[] pageNumbers = calculatePageNumbers(currentPage, totalPages, displayPageCount);
            pageInfo.setPageNumbers(pageNumbers);
            
        } else {
            pageInfo.setTotalPages(0);
            pageInfo.setHasNext(false);
            pageInfo.setHasPrev(false);
            pageInfo.setIsFirst(true);
            pageInfo.setIsLast(true);
            pageInfo.setStartRow(0);
            pageInfo.setEndRow(0);
            pageInfo.setFirstPage(1);
            pageInfo.setLastPage(1);
            pageInfo.setPageNumbers(new Integer[]{1});
        }
        
        return pageInfo;
    }
    
    /**
     * 표시할 페이지 번호 배열 계산
     * 
     * 예: currentPage=5, totalPages=10, displayCount=5
     * => [3, 4, 5, 6, 7]
     */
    private static Integer[] calculatePageNumbers(int currentPage, int totalPages, int displayCount) {
        if (totalPages <= displayCount) {
            // 전체 페이지가 표시 개수보다 적으면 전체 표시
            Integer[] pages = new Integer[totalPages];
            for (int i = 0; i < totalPages; i++) {
                pages[i] = i + 1;
            }
            return pages;
        }
        
        // 시작 페이지 계산
        int start = Math.max(1, currentPage - displayCount / 2);
        int end = Math.min(totalPages, start + displayCount - 1);
        
        // 끝에 가까우면 시작 조정
        if (end - start < displayCount - 1) {
            start = Math.max(1, end - displayCount + 1);
        }
        
        // 배열 생성
        Integer[] pages = new Integer[end - start + 1];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = start + i;
        }
        
        return pages;
    }
    
    /**
     * 페이지 정보 문자열 (예: "1-20 / 총 150개")
     */
    public String getPageInfoText() {
        if (totalCount == null || totalCount == 0) {
            return "데이터 없음";
        }
        return String.format("%d-%d / 총 %,d개", startRow, endRow, totalCount);
    }
    
    /**
     * 간단한 페이지 정보 (예: "1/10 페이지")
     */
    public String getSimplePageInfo() {
        return String.format("%d/%d 페이지", currentPage, totalPages);
    }
}