// shadcn-ui util: className 병합
export function cn(...inputs) {
  return inputs.filter(Boolean).join(' ');
}
