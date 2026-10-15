import { reactive } from 'vue'
const user = reactive({ getUser: { id: 1 }, getRoles: [] as string[], getPermissions: new Set(['rd:pickup-slip:create']) })
export const useUserStore = () => user
