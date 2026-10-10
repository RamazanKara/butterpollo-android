#pragma once
#include <stdatomic.h>

// Phase-lock reports to Rubylight hosts (android_control.c).
extern atomic_bool PhaseLockReportsEnabled;
void resetPhaseLockReports(void);
