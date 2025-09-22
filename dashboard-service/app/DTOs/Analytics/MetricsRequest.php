<?php

namespace App\DTOs\Analytics;

use Carbon\Carbon;

class MetricsRequest
{
    private Carbon $startDate;
    private Carbon $endDate;
    private string $granularity;

    public function __construct(?string $from = null, ?string $to = null, string $granularity = 'day')
    {
        $this->startDate = $from ? Carbon::parse($from) : Carbon::now()->subDays(7);
        $this->endDate = $to ? Carbon::parse($to) : Carbon::now();
        $this->granularity = $granularity;
    }

    public function getStartDate(): Carbon
    {
        return $this->startDate;
    }

    public function getEndDate(): Carbon
    {
        return $this->endDate;
    }

    public function getGranularity(): string
    {
        return $this->granularity;
    }

    public function toArray(): array
    {
        return [
            'from' => $this->startDate->toISOString(),
            'to' => $this->endDate->toISOString(),
            'granularity' => $this->granularity,
        ];
    }
}
