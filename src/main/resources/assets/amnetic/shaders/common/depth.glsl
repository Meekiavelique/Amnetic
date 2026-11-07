#ifdef AMNETIC_REVERSED_DEPTH
#define IS_SKY(d) ((d) <= 0.0)
#define DEPTH_NEARER(a, b) ((a) > (b))
#define FORWARD_DEPTH(d) (1.0 - (d))
#else
#define IS_SKY(d) ((d) >= 1.0)
#define DEPTH_NEARER(a, b) ((a) < (b))
#define FORWARD_DEPTH(d) (d)
#endif
