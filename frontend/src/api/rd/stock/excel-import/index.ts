import request from '@/config/axios'

export interface RdExcelImportTaskVO {
  id: number
  fileName: string
  sheetName: string
  bizDate: string
  status: string
  totalRows: number
  validRows: number
  failedRows: number
  skippedRows: number
  importedRows: number
  confirmTime?: string
  remark?: string
  createTime: string
}

export interface RdExcelImportRowVO {
  id: number
  taskId: number
  rowNo: number
  locationCode?: string
  customerName?: string
  productModel?: string
  furnaceNo?: string
  furnaceLayer?: string
  sourceOpeningQty?: number
  warehouseQty?: number
  packageWeight?: number
  stockWeight?: number
  status: string
  errorMessage?: string
  stockLotId?: number
}

export interface RdExcelImportErrorVO {
  id: number
  taskId: number
  rowNo: number
  fieldName: string
  rawValue?: string
  errorMessage: string
}

export const RdExcelImportApi = {
  precheck: async (data: FormData) => {
    return await request.upload({ url: '/rd/excel-import/precheck', data })
  },
  confirm: async (id: number) => {
    return await request.put({ url: '/rd/excel-import/confirm', params: { id } })
  },
  get: async (id: number) => {
    return await request.get({ url: '/rd/excel-import/get', params: { id } })
  },
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/excel-import/page', params })
  },
  getRowPage: async (params: any) => {
    return await request.get({ url: '/rd/excel-import/row-page', params })
  },
  getErrorPage: async (params: any) => {
    return await request.get({ url: '/rd/excel-import/error-page', params })
  },
  exportErrors: async (taskId: number) => {
    return await request.download({ url: '/rd/excel-import/export-errors', params: { taskId } })
  }
}
