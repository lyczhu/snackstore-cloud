package com.lawyus.snackstore.common.response;

import java.io.Serial;
import java.util.List;

/**
 * 分页查询结果类
 *
 * @param <T> 返回数据类型
 */
public class PageResult<T> extends Result<List<T>> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    private Long pageNum;

    /**
     * 每页数量
     */
    private Long pageSize;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 获取当前页码
     *
     * @return 当前页码
     */
    public Long getPageNum() {
        return pageNum;
    }

    /**
     * 设置当前页码
     *
     * @param pageNum 当前页码
     */
    public void setPageNum(Long pageNum) {
        this.pageNum = pageNum;
    }

    /**
     * 获取每页数量
     *
     * @return 每页数量
     */
    public Long getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页数量
     *
     * @param pageSize 每页数量
     */
    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * 获取总记录数
     *
     * @return 总记录数
     */
    public Long getTotal() {
        return total;
    }

    /**
     * 设置总记录数
     *
     * @param total 总记录数
     */
    public void setTotal(Long total) {
        this.total = total;
    }

    /**
     * 获取总页数
     *
     * @return 总页数
     */
    public Long getPages() {
        return pages;
    }

    /**
     * 设置总页数
     *
     * @param pages 总页数
     */
    public void setPages(Long pages) {
        this.pages = pages;
    }

    /**
     * 成功返回分页结果
     *
     * @param data     分页数据
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @param total    总记录数
     * @param <T>      数据类型
     * @return PageResult
     */
    public static <T> PageResult<T> success(List<T> data, Long pageNum, Long pageSize, Long total) {
        PageResult<T> result = new PageResult<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotal(total);
        result.setPages(pageSize == null || pageSize <= 0 ? 0L : (total + pageSize - 1) / pageSize);
        return result;
    }
}