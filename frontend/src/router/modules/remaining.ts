import { Layout } from '@/utils/routerHelper'

const { t } = useI18n()

const remainingRouter: AppRouteRecordRaw[] = [
  {
    path: '/redirect',
    component: Layout,
    name: 'RedirectRoot',
    meta: { hidden: true, noTagsView: true },
    children: [
      {
        path: '/redirect/:path(.*)',
        name: 'Redirect',
        component: () => import('@/views/Redirect/Redirect.vue'),
        meta: {}
      }
    ]
  },
  {
    path: '/',
    component: Layout,
    redirect: '/index',
    name: 'Home',
    meta: {},
    children: [
      {
        path: 'index',
        component: () => import('@/views/Home/Index.vue'),
        name: 'Index',
        meta: {
          title: 'ERP 工作台',
          icon: 'ep:home-filled',
          noCache: false,
          affix: true
        }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    name: 'UserInfo',
    meta: { hidden: true },
    children: [
      {
        path: 'profile',
        component: () => import('@/views/Profile/Index.vue'),
        name: 'Profile',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:user',
          title: t('common.profile')
        }
      }
    ]
  },
  {
    path: '/dict',
    component: Layout,
    name: 'DictDataRoot',
    meta: { hidden: true },
    children: [
      {
        path: 'type/data/:dictType',
        component: () => import('@/views/system/dict/data/index.vue'),
        name: 'SystemDictData',
        meta: {
          title: '字典数据',
          noCache: true,
          hidden: true,
          canTo: true,
          activeMenu: '/system/dict'
        }
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/Login/Login.vue'),
    name: 'Login',
    meta: { hidden: true, title: t('router.login'), noTagsView: true }
  },
  {
    path: '/social-login',
    component: () => import('@/views/Login/SocialLogin.vue'),
    name: 'SocialLogin',
    meta: { hidden: true, title: t('router.socialLogin'), noTagsView: true }
  },
  {
    path: '/pkg/:packageCode',
    component: () => import('@/views/rd/trace/package/index.vue'),
    name: 'PackageTrace',
    meta: { hidden: true, title: '货物数字身份', noTagsView: true }
  },
  {
    path: '/pda',
    component: () => import('@/views/rd/pda/workspace/layout.vue'),
    name: 'PdaRoot',
    meta: { hidden: true, title: '现场工作台', noTagsView: true },
    children: [
      {
        path: '',
        name: 'PdaWorkspace',
        component: () => import('@/views/rd/pda/workspace/index.vue'),
        meta: { title: '现场工作台', noTagsView: true }
      },
      {
        path: 'production',
        name: 'PdaProduction',
        component: () => import('@/views/rd/pda/production/index.vue'),
        meta: { title: '生产录入', noTagsView: true }
      },
      {
        path: 'driver-putaway',
        name: 'PdaDriverPutaway',
        component: () => import('@/views/rd/pda/putaway/index.vue'),
        meta: { title: '本人上架任务', noTagsView: true }
      },
      {
        path: 'quality',
        name: 'PdaQuality',
        component: () => import('@/views/rd/pda/quality/index.vue'),
        meta: { title: '质量检验', noTagsView: true }
      },
      {
        path: 'driver-loading',
        name: 'PdaDriverLoading',
        component: () => import('@/views/rd/pda/loading/index.vue'),
        meta: { title: '本人装车任务', noTagsView: true }
      }
    ]
  },
  {
    path: '/403',
    component: () => import('@/views/Error/403.vue'),
    name: 'NoAccess',
    meta: { hidden: true, title: '403', noTagsView: true }
  },
  {
    path: '/404',
    component: () => import('@/views/Error/404.vue'),
    name: 'NoFound',
    meta: { hidden: true, title: '404', noTagsView: true }
  },
  {
    path: '/500',
    component: () => import('@/views/Error/500.vue'),
    name: 'Error',
    meta: { hidden: true, title: '500', noTagsView: true }
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/Error/404.vue'),
    name: 'NotFound',
    meta: { title: '404', hidden: true, breadcrumb: false }
  }
]

export default remainingRouter
