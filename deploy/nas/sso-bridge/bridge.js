(() => {
  'use strict'

  const STATE_PATTERN = /^[A-Za-z0-9_-]{32,128}$/
  const TOKEN_LOCATIONS = new Set(['url', 'header'])
  const HANDOFF_PATH = '/gak/api/auth/nas-sso/handoff'
  const FALLBACK_PATH = '/gak/syslogin?reason=nas_bridge_unavailable'

  function getState() {
    const state = new URL(window.location.href).searchParams.get('state') || ''
    if (!STATE_PATTERN.test(state)) {
      throw new Error('SSO state is invalid')
    }
    return state
  }

  function getNasAccessInfo() {
    const rawConfig = window.localStorage.getItem('proConfig')
    const parsedConfig = JSON.parse(rawConfig || 'null')
    const accessInfo = parsedConfig?.accessInfo
    const nasToken = typeof accessInfo?.api_token === 'string'
      ? accessInfo.api_token.trim()
      : ''
    const tokenWhere = typeof accessInfo?.token_where === 'string'
      ? accessInfo.token_where.trim().toLowerCase()
      : 'url'
    if (!nasToken || !TOKEN_LOCATIONS.has(tokenWhere)) {
      throw new Error('NAS access info is invalid')
    }
    return {nasToken, tokenWhere}
  }

  function appendHiddenField(form, name, value) {
    const field = document.createElement('input')
    field.type = 'hidden'
    field.name = name
    field.value = value
    form.appendChild(field)
  }

  function submitHandoff(state, accessInfo) {
    const form = document.createElement('form')
    form.method = 'post'
    form.action = HANDOFF_PATH
    appendHiddenField(form, 'nasToken', accessInfo.nasToken)
    appendHiddenField(form, 'tokenWhere', accessInfo.tokenWhere)
    appendHiddenField(form, 'state', state)
    document.body.appendChild(form)
    form.submit()
  }

  try {
    submitHandoff(getState(), getNasAccessInfo())
  } catch (error) {
    window.location.replace(FALLBACK_PATH)
  }
})()
