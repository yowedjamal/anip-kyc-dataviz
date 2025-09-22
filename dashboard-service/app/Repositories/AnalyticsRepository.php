<?php

namespace App\Repositories;

interface AnalyticsRepository
{
    /**
     * Return aggregated metrics between two datetimes as array
     *
     * @param string $from ISO8601
     * @param string $to ISO8601
     * @return array
     */
    public function aggregateMetrics(string $from, string $to): array;

    /**
     * Return timeseries aggregated points between two datetimes
     * @param string $from
     * @param string $to
     * @param string $interval
     * @return array
     */
    public function timeSeries(string $from, string $to, string $interval = '1 hour'): array;
}
