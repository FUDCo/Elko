#include "defs.h"
#include "code.h"

int g_pc = 0;
int g_addr = 0;
u1 *g_code = null;

  int
argb()
{
    return g_code[g_pc++];
}

  int
argw()
{
    u1 hi = g_code[g_pc++];
    u1 lo = g_code[g_pc++];
    return (hi << 8) | lo;
}

  int
argl()
{
    u1 b4 = g_code[g_pc++];
    u1 b3 = g_code[g_pc++];
    u1 b2 = g_code[g_pc++];
    u1 b1 = g_code[g_pc++];
    return (b4 << 24) | (b3 << 8) | (b2 << 8) | b1;
}
