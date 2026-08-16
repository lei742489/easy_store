import { h } from 'vue';
import Decimal from 'decimal.js';

export function formatPrice(price: number | string | null | undefined): string {
  const num = Number(price);
  if (Number.isNaN(num)) return '0.00';

  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(num);
}

export function addPrice(a: number, b: number): number {
  return Number(new Decimal(a).plus(b).toFixed(2));
}

export function divPrice(a: number, b: number): number {
  if (b === 0) {
    return 0; // 或者你想抛错
  }
  return Number(new Decimal(a).div(b).toFixed(2));
}

export function mulPrice(a: number, b: number): number {
  return Number(
    new Decimal(a).times(b).toDecimalPlaces(2, Decimal.ROUND_DOWN).toString()
  );
}

export function getPriceStrByH(price: number | string | null | undefined) {
  if (!price) {
    return 0;
  }
  const priceStr = formatPrice(price);
  return h(
    'span',
    {
      style: {
        color: price < 0 ? 'red' : undefined,
      },
    },
    `￥ ${priceStr}`
  );
}
