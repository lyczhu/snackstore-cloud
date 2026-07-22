package com.lawyus.snackstore.statistics.constant;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class StatisticsCacheConstants {

    private StatisticsCacheConstants() {
    }

    private static final String DASHBOARD_KEY_PREFIX = "snackstore:statistics:dashboard:";

    private static final String TREND_KEY_PREFIX = "snackstore:statistics:trend:";

    private static final String TOP_PRODUCTS_KEY_PREFIX = "snackstore:statistics:top:products:";

    private static final String TOP_CATEGORIES_KEY_PREFIX = "snackstore:statistics:top:categories:";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final long DASHBOARD_CACHE_TTL_SECONDS = 60;

    public static final long TREND_CACHE_TTL_SECONDS = 300;

    public static final long TOP_CACHE_TTL_SECONDS = 300;

    public static String dashboardKey(LocalDate date) {
        return DASHBOARD_KEY_PREFIX + date.format(DATE_FORMATTER);
    }

    public static String trendKey(LocalDate date, int days) {
        return TREND_KEY_PREFIX + date.format(DATE_FORMATTER) + ":" + days;
    }

    public static String topProductsKey(LocalDate date, int limit) {
        return TOP_PRODUCTS_KEY_PREFIX + date.format(DATE_FORMATTER) + ":" + limit;
    }

    public static String topCategoriesKey(LocalDate date, int limit) {
        return TOP_CATEGORIES_KEY_PREFIX + date.format(DATE_FORMATTER) + ":" + limit;
    }
}
