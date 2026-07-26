#!/usr/bin/env node

import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import {fileURLToPath} from 'node:url'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const bridgeSource = fs.readFileSync(
  path.resolve(scriptDirectory, '../sso-bridge/bridge.js'),
  'utf8'
)
const bridgeHtml = fs.readFileSync(
  path.resolve(scriptDirectory, '../sso-bridge/bridge.html'),
  'utf8'
)
const nginxTemplate = fs.readFileSync(
  path.resolve(scriptDirectory, '../nginx/gak_sso_bridge.conf.template'),
  'utf8'
)
const validState = 'a'.repeat(43)

function executeBridge(rawConfig) {
  const submittedForms = []
  const redirects = []
  const statusParagraph = {textContent: ''}
  const document = {
    body: {
      appendChild() {
      }
    },
    querySelector() {
      return statusParagraph
    },
    createElement(tagName) {
      if (tagName === 'form') {
        const form = {
          children: [],
          appendChild(field) {
            this.children.push(field)
          },
          submit() {
            submittedForms.push(this)
          }
        }
        return form
      }
      return {}
    }
  }
  const window = {
    localStorage: {
      getItem(key) {
        assert.equal(key, 'proConfig')
        return rawConfig
      }
    },
    location: {
      href: `http://greennas:9999/gak-sso/bridge.html?state=${validState}`,
      replace(target) {
        redirects.push(target)
      }
    }
  }
  vm.runInNewContext(bridgeSource, {
    URL,
    Set,
    document,
    window
  })
  return {submittedForms, redirects}
}

const validConfig = JSON.stringify({
  accessInfo: {
    api_token: 'sensitive-nas-token',
    token_where: 'header'
  },
  proUserInfo: {
    username: 'forged-user'
  }
})
const success = executeBridge(validConfig)
assert.equal(success.redirects.length, 0)
assert.equal(success.submittedForms.length, 1)
assert.equal(success.submittedForms[0].method, 'post')
assert.equal(success.submittedForms[0].action, '/gak/api/auth/nas-sso/handoff')
assert.equal(success.submittedForms[0].action.includes('sensitive-nas-token'), false)
assert.deepEqual(
  success.submittedForms[0].children.map(({name, value}) => [name, value]),
  [
    ['nasToken', 'sensitive-nas-token'],
    ['tokenWhere', 'header'],
    ['state', validState]
  ]
)

for (const invalidConfig of [
  null,
  '{broken-json',
  JSON.stringify({accessInfo: {token_where: 'url'}}),
  JSON.stringify({accessInfo: {api_token: 'token', token_where: 'cookie'}})
]) {
  const failure = executeBridge(invalidConfig)
  assert.equal(failure.submittedForms.length, 0)
  assert.deepEqual(failure.redirects, ['/gak/syslogin?reason=nas_bridge_unavailable'])
}

assert.equal(bridgeSource.includes('proUserInfo'), false)
assert.equal(bridgeHtml.includes('greennas'), false)
assert.equal(bridgeHtml.includes("form-action 'self'"), true)
assert.equal(nginxTemplate.includes('location ^~ /gak/'), true)
assert.equal(nginxTemplate.includes('127.0.0.1:__GAK_WEB_PORT__'), true)
console.log('SSO bridge tests passed.')
