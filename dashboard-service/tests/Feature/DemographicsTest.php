<?php

namespace Tests\Feature;

use Tests\TestCase;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Response;

/**
 * Test contractuel pour l'endpoint GET /analytics/demographics
 * 
 * Contract: GET /analytics/demographics
 * - Input: ?date_from=iso_date&date_to=iso_date&breakdown=age_group|gender|region
 * - Output 200: {demographics: array, anonymization: object, metadata: object}
 * - Output 400: {error: "INVALID_REQUEST", message: string, details: object}
 * - Output 401: {error: "UNAUTHORIZED", message: string}
 * 
 * CE TEST DOIT ÉCHOUER avant implémentation du AnalyticsController
 */
class DemographicsTest extends TestCase
{
    use RefreshDatabase;

    public function test_demographics_success_age_breakdown(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer mock-jwt-token',
            'Accept' => 'application/json',
        ])->get('/analytics/demographics?breakdown=age_group');

        $response->assertStatus(Response::HTTP_OK)
                ->assertJsonStructure([
                    'demographics' => [
                        '*' => [
                            'category',
                            'count',
                            'percentage',
                            'anonymized'
                        ]
                    ],
                    'anonymization' => [
                        'method',
                        'k_anonymity',
                        'applied_at'
                    ],
                    'metadata' => [
                        'total_records',
                        'breakdown_type',
                        'period'
                    ]
                ])
                ->assertJson([
                    'metadata' => [
                        'breakdown_type' => 'age_group'
                    ],
                    'anonymization' => [
                        'method' => 'k-anonymity',
                        'k_anonymity' => fn($k) => is_numeric($k) && $k >= 5
                    ]
                ]);
    }

    public function test_demographics_success_gender_breakdown(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer mock-jwt-token',
            'Accept' => 'application/json',
        ])->get('/analytics/demographics?breakdown=gender');

        $response->assertStatus(Response::HTTP_OK)
                ->assertJsonStructure([
                    'demographics',
                    'anonymization',
                    'metadata'
                ])
                ->assertJson([
                    'metadata' => [
                        'breakdown_type' => 'gender'
                    ]
                ]);
    }

    public function test_demographics_invalid_request_invalid_breakdown(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer mock-jwt-token',
            'Accept' => 'application/json',
        ])->get('/analytics/demographics?breakdown=invalid_breakdown');

        $response->assertStatus(Response::HTTP_BAD_REQUEST)
                ->assertJson([
                    'error' => 'INVALID_REQUEST',
                    'message' => 'Invalid breakdown type',
                    'details' => [
                        'breakdown' => 'INVALID_VALUE'
                    ]
                ]);
    }

    public function test_demographics_insufficient_data_for_anonymization(): void
    {
        $response = $this->withHeaders([
            'Authorization' => 'Bearer mock-jwt-token',
            'Accept' => 'application/json',
        ])->get('/analytics/demographics?breakdown=age_group&region=small_region');

        $response->assertStatus(Response::HTTP_OK)
                ->assertJsonStructure([
                    'demographics',
                    'anonymization',
                    'metadata',
                    'warnings'
                ])
                ->assertJson([
                    'warnings' => [
                        'INSUFFICIENT_DATA_FOR_ANONYMIZATION'
                    ],
                    'anonymization' => [
                        'suppressed_categories' => fn($count) => is_numeric($count) && $count > 0
                    ]
                ]);
    }
}