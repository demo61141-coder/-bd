export class OneEuroScalarFilter {
  private prevValue: number | null = null;
  private prevDeriv = 0;
  private prevTime: number | null = null;

  constructor(
    public minCutoff = 1.2,
    public beta = 0.04,
    private dCutoff = 1.0
  ) {}

  private alpha(cutoff: number, dt: number): number {
    const tau = 1.0 / (2 * Math.PI * Math.max(cutoff, 0.001));
    return 1.0 / (1.0 + tau / Math.max(dt, 0.0001));
  }

  filter(value: number, timestampSec: number): number {
    if (this.prevValue === null || this.prevTime === null) {
      this.prevValue = value;
      this.prevTime = timestampSec;
      return value;
    }
    const dt = Math.min(Math.max(timestampSec - this.prevTime, 0.001), 0.25);
    const rawDeriv = (value - this.prevValue) / dt;
    const aDeriv = this.alpha(this.dCutoff, dt);
    const smoothedDeriv = this.prevDeriv + aDeriv * (rawDeriv - this.prevDeriv);

    const cutoff = this.minCutoff + this.beta * Math.abs(smoothedDeriv);
    const aVal = this.alpha(cutoff, dt);
    const filtered = this.prevValue + aVal * (value - this.prevValue);

    this.prevValue = filtered;
    this.prevDeriv = smoothedDeriv;
    this.prevTime = timestampSec;
    return filtered;
  }

  reset() {
    this.prevValue = null;
    this.prevDeriv = 0;
    this.prevTime = null;
  }
}

export class OneEuroLandmarkBank {
  private xFilters = new Map<number, OneEuroScalarFilter>();
  private yFilters = new Map<number, OneEuroScalarFilter>();
  private scalarFilters = new Map<string, OneEuroScalarFilter>();

  filterPoint(index: number, x: number, y: number, strength: number, tSec: number): { x: number; y: number } {
    if (strength <= 0.02) return { x, y };
    const minCutoff = Math.max(0.35, 3.8 - strength * 3.35);
    const beta = Math.max(0.015, 0.15 - strength * 0.11);

    let fx = this.xFilters.get(index);
    if (!fx) {
      fx = new OneEuroScalarFilter(minCutoff, beta);
      this.xFilters.set(index, fx);
    }
    let fy = this.yFilters.get(index);
    if (!fy) {
      fy = new OneEuroScalarFilter(minCutoff, beta);
      this.yFilters.set(index, fy);
    }
    fx.minCutoff = minCutoff;
    fx.beta = beta;
    fy.minCutoff = minCutoff;
    fy.beta = beta;

    return {
      x: fx.filter(x, tSec),
      y: fy.filter(y, tSec)
    };
  }

  filterScalar(key: string, value: number, strength: number, tSec: number): number {
    if (strength <= 0.02) return value;
    const minCutoff = Math.max(0.35, 3.8 - strength * 3.35);
    const beta = Math.max(0.015, 0.15 - strength * 0.11);
    let f = this.scalarFilters.get(key);
    if (!f) {
      f = new OneEuroScalarFilter(minCutoff, beta);
      this.scalarFilters.set(key, f);
    }
    f.minCutoff = minCutoff;
    f.beta = beta;
    return f.filter(value, tSec);
  }

  reset() {
    this.xFilters.clear();
    this.yFilters.clear();
    this.scalarFilters.clear();
  }
}
