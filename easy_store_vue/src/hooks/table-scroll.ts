const getAdaptiveTableScrollY = (minHeight: number, reservedHeight = 430) =>
  `max(${minHeight}px, calc(100vh - ${reservedHeight}px))`;

export default getAdaptiveTableScrollY;
