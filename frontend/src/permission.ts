import router from './router'
import type { RouteRecordRaw } from 'vue-router'
import { isRelogin } from '@/config/axios/service'
import { getAccessToken } from '@/utils/auth'
import { useTitle } from '@/hooks/web/useTitle'
import { useNProgress } from '@/hooks/web/useNProgress'
import { usePageLoading } from '@/hooks/web/usePageLoading'
import { useDictStoreWithOut } from '@/store/modules/dict'
import { useUserStoreWithOut } from '@/store/modules/user'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { parseRouteLocation } from '@/utils/routeParams'
import { canViewWarehouseSpace } from '@/views/rd/stock/location-map/warehouseSpaceAccess'

const { start, done } = useNProgress()

const { loadStart, loadDone } = usePageLoading()

// 路由不重定向白名单
const whiteList = [
  '/login',
  '/social-login',
  '/auth-redirect',
  '/bind',
  '/register',
  '/oauthLogin/gitee'
]

const isPublicRoute = (path: string) => whiteList.includes(path) || path.startsWith('/pkg/')

// 路由加载前
router.beforeEach(async (to, from, next) => {
  start()
  loadStart()
  if (getAccessToken()) {
    if (to.path === '/login') {
      next({ path: '/' })
    } else {
      const dictStore = useDictStoreWithOut()
      const userStore = useUserStoreWithOut()
      const permissionStore = usePermissionStoreWithOut()
      // 异步加载字典
      // 动态路由加载完成后重试当前导航。
      if (!dictStore.getIsSetDict) {
        dictStore.setDictMap().then()
      }
      if (!userStore.getIsSetUser) {
        isRelogin.show = true
        await userStore.setUserInfoAction()
        isRelogin.show = false
        // 后端过滤菜单
        await permissionStore.generateRoutes()
        permissionStore.getAddRouters.forEach((route) => {
          router.addRoute(route as unknown as RouteRecordRaw) // 动态添加可访问路由表
        })
        if (
          to.name === 'RdWarehouseSpace' &&
          !canViewWarehouseSpace(userStore.getRoles, userStore.getPermissions)
        ) {
          next('/403')
          return
        }
        const redirectPath = from.query.redirect
        // 修复跳转时不带参数的问题
        const redirect = typeof redirectPath === 'string' ? redirectPath : to.fullPath
        const redirectLocation = parseRouteLocation(redirect)
        // 当前导航可能已被静态 404 路由匹配；动态路由注册后必须按原地址重新匹配，
        // 不能继续复用旧的 route name/matched，否则刷新业务页会停在 404。
        next({ ...redirectLocation, replace: true })
      } else {
        if (
          to.name === 'RdWarehouseSpace' &&
          !canViewWarehouseSpace(userStore.getRoles, userStore.getPermissions)
        ) {
          next('/403')
          return
        }
        next()
      }
    }
  } else {
    if (isPublicRoute(to.path)) {
      next()
    } else {
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`) // 否则全部重定向到登录页
    }
  }
})

router.afterEach((to) => {
  useTitle(to?.meta?.title as string)
  done() // 结束Progress
  loadDone()
})
