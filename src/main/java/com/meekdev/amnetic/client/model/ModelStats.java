package com.meekdev.amnetic.client.model;

// what the model pass drew last frame: draw calls, the model parts they drew between them, their
// triangles, and how many parts were drawn at each level of detail, 0 the full one
public record ModelStats(int draws, int shadowDraws, int instances, long triangles, int[] levels) {}
