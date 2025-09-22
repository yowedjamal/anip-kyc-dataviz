<?php

namespace App\Repositories\Eloquent;

use App\Models\AnalyticsData;
use App\Repositories\AnalyticsRepository;
use Carbon\Carbon;
use Illuminate\Support\Facades\DB;

class AnalyticsEloquentRepository implements AnalyticsRepository
{
    public function aggregateMetrics(string $from, string $to): array
    {
        $fromDt = Carbon::parse($from);
        $toDt = Carbon::parse($to);

        $total = AnalyticsData::whereBetween('observed_at', [$fromDt, $toDt])->count();
        $by_status = AnalyticsData::select('status', DB::raw('count(*) as cnt'))
            ->whereBetween('observed_at', [$fromDt, $toDt])
            ->groupBy('status')
            ->get()
            ->pluck('cnt', 'status')
            ->toArray();

        return [
            'total' => $total,
            'by_status' => $by_status,
        ];
    }

    public function timeSeries(string $from, string $to, string $interval = '1 hour'): array
    {
        // Use date_trunc for portability. If TimescaleDB is present, more efficient functions may be used.
        $fmt = $this->intervalToDateTrunc($interval);

        $rows = AnalyticsData::select(DB::raw("date_trunc('{$fmt}', observed_at) as ts"), DB::raw('count(*) as cnt'))
            ->whereBetween('observed_at', [Carbon::parse($from), Carbon::parse($to)])
            ->groupBy('ts')
            ->orderBy('ts')
            ->get();

        $result = [];
        foreach ($rows as $r) {
            $result[] = [
                'timestamp' => Carbon::parse($r->ts)->toISOString(),
                'count' => (int) $r->cnt,
            ];
        }

        return $result;
    }

    private function intervalToDateTrunc(string $interval): string
    {
        if (str_contains($interval, 'day')) {
            return 'day';
        }
        if (str_contains($interval, 'hour')) {
            return 'hour';
        }
        if (str_contains($interval, 'minute')) {
            return 'minute';
        }
        return 'hour';
    }
}
