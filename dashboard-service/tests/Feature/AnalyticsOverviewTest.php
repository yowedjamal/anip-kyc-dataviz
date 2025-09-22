<?php

namespace Tests\Feature;

use Tests\TestCase;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Response;

/**
 * Test contractuel pour l'endpoint GET /analytics/overview
 * 
 * Contract: GET /analytics/overview
 * - Input: ?date_from=iso_date&date_to=iso_date&granularity=hour|day|week|month
 * - Output 200: {summary: object, trends: array, metrics: object}
 * - Output 400: {error: "INVALID_REQUEST", message: string, details: object}
 * - Output 401: {error: "UNAUTHORIZED", message: string}
 * 
 * CE TEST DOIT ÉCHOUER avant implémentation du AnalyticsController
 */
class AnalyticsOverviewTest extends TestCase
{
    use RefreshDatabase;

    public function test_analytics_overview_success_default_period(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer mock-jwt-token',
            'Accept' => 'application/json',
        ])->get('/analytics/overview');

        $response->assertStatus(Response::HTTP_OK)
                ->assertJsonStructure([
                    'summary' => [
                        'total_sessions',
                        'successful_verifications',
                        'failed_verifications',
                        'success_rate',
                        'average_processing_time'
                    ],
                    'trends' => [
                        '*' => [
                            'timestamp',
                            'session_count',
                            'success_count',
                            'success_rate'
                        ]
                    ],
                    'metrics' => [
                        'peak_hours',
                        'most_common_document_types',
                        'geographic_distribution'
                    ]
                ])
                ->assertJson([
                    'summary' => [
                        'success_rate' => fn($rate) => is_numeric($rate) && $rate >= 0 && $rate <= 100
                    ]
                ]);
    }

    public function test_analytics_overview_success_custom_period(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer mock-jwt-token',
            'Accept' => 'application/json',
        ])->get('/analytics/overview?date_from=2023-09-01T00:00:00Z&date_to=2023-09-30T23:59:59Z&granularity=day');

        $response->assertStatus(Response::HTTP_OK)
                ->assertJsonStructure([
                    'summary',
                    'trends',
                    'metrics',
                    'period' => [
                        'from',
                        'to',
                        'granularity'
                    ]
                ])
                ->assertJson([
                    'period' => [
                        'from' => '2023-09-01T00:00:00Z',
                        'to' => '2023-09-30T23:59:59Z',
                        'granularity' => 'day'
                    ]
                ]);
    }

    public function test_analytics_overview_invalid_request_invalid_date_format(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer mock-jwt-token',
            'Accept' => 'application/json',
        ])->get('/analytics/overview?date_from=invalid-date');

        $response->assertStatus(Response::HTTP_BAD_REQUEST)
                ->assertJson([
                    'error' => 'INVALID_REQUEST',
                    'message' => 'Invalid date format',
                    'details' => [
                        'date_from' => 'INVALID_FORMAT'
                    ]
                ]);
    }

    public function test_analytics_overview_invalid_request_invalid_granularity(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer mock-jwt-token',
            'Accept' => 'application/json',
        ])->get('/analytics/overview?granularity=invalid');

        $response->assertStatus(Response::HTTP_BAD_REQUEST)
                ->assertJson([
                    'error' => 'INVALID_REQUEST',
                    'message' => 'Invalid granularity value',
                    'details' => [
                        'granularity' => 'INVALID_VALUE'
                    ]
                ]);
    }

    public function test_analytics_overview_unauthorized_missing_token(): void
    {
        $response = $this->get('/analytics/overview');

        $response->assertStatus(Response::HTTP_UNAUTHORIZED)
                ->assertJson([
                    'error' => 'UNAUTHORIZED',
                    'message' => 'Authentication token required'
                ]);
    }

    public function test_analytics_overview_unauthorized_invalid_token(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer invalid-token',
            'Accept' => 'application/json',
        ])->get('/analytics/overview');

        $response->assertStatus(Response::HTTP_UNAUTHORIZED)
                ->assertJson([
                    'error' => 'UNAUTHORIZED',
                    'message' => 'Invalid authentication token'
                ]);
    }

    public function test_analytics_overview_success_with_filters(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer mock-jwt-token',
            'Accept' => 'application/json',
        ])->get('/analytics/overview?region=dakar&verification_type=FULL_KYC');

        $response->assertStatus(Response::HTTP_OK)
                ->assertJsonStructure([
                    'summary',
                    'trends',
                    'metrics',
                    'filters' => [
                        'region',
                        'verification_type'
                    ]
                ])
                ->assertJson([
                    'filters' => [
                        'region' => 'dakar',
                        'verification_type' => 'FULL_KYC'
                    ]
                ]);
    }
}