package com.company.tai.dashboard.dto;

import java.util.List;

// daysWithActivity is how many distinct days in the requested window actually had at least one
// completed sale — the frontend uses this (not `days`) to decide whether to show a "limited
// data" note, since a 30-day window with only 2 active days would be a misleading trend line
// presented as if it were a real pattern.
public record SalesTrendDto(int days, int daysWithActivity, List<SalesTrendPointDto> points) {}
