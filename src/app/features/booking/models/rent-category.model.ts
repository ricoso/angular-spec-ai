export interface RentCategory {
  id: string;
  groupId: string;
  name: string;
  description: string;
  /** REQ-025 (f): optional, kommt aus SoftNet wenn Preis hinterlegt. */
  price?: number;
  /** REQ-025 (f): ISO-4217 (z. B. "EUR"). */
  currency?: string;
  /** REQ-025 (f): "day" | "hour". */
  priceUnit?: string;
  brands: string[];
  branches: string[];
}
