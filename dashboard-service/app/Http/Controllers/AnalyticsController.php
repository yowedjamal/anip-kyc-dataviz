<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;
use App\Services\AnalyticsService;
use App\DTOs\Analytics\MetricsRequest;
use App\DTOs\Analytics\MetricsResponse;
use Carbon\Carbon;

class AnalyticsController extends Controller
{
    private ?AnalyticsService $analyticsService;

    public function __construct(?AnalyticsService $analyticsService = null)
    {
        $this->analyticsService = $analyticsService;
    }

    public function overview(Request $request): JsonResponse
    {
        // If service not bound, return a stub response (useful during initial dev)
        if (!$this->analyticsService) {
            return response()->json([
                'summary' => [
                    'total_sessions' => 0,
                    'successful_verifications' => 0,
                    'failed_verifications' => 0,
                    'success_rate' => 0,
                    'average_processing_time' => 0
                ],
                'trends' => [],
                'metrics' => [],
                'period' => [
                    'from' => $request->query('date_from'),
                    'to' => $request->query('date_to'),
                    'granularity' => $request->query('granularity', 'day')
                ]
            ]);
        }

        $dto = new MetricsRequest(
            $request->query('date_from'),
            $request->query('date_to'),
            $request->query('granularity', '1 day')
        );

        $result = $this->analyticsService->getDashboardMetrics($dto);

        if ($result instanceof MetricsResponse) {
            return response()->json($result->toArray());
        }

        // If service returns array, pass-through
        return response()->json($result);
    }
}
