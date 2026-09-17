package com.company.tai.dashboard.dto;

import java.time.Instant;

// description is deliberately non-monetary (no amounts) so this feed is safe to show to every
// role without a separate STAFF-redacted variant.
public record RecentActivityDto(String type, String description, Instant timestamp) {}
