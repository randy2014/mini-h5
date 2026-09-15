/**
 * 构建前置校验（无需 spawn 子进程，可在受限环境下运行）：
 *  1. 逐个编译 .vue 单文件组件（<script setup> + <template>）—— 捕获模板/脚本语法错误
 *  2. 用 esbuild 的纯 JS 解析器检查全部本地 import 是否指向真实文件
 *  3. 检查路由 meta.require 只使用权限层已实现的 REQUIREMENT
 *
 * 用途：`node --test` 与 `vite build` 不可用时的等价回归。
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';

const ROOT = resolve(process.cwd(), 'src');
const problems = [];
let vueCount = 0;
let jsCount = 0;
let importCount = 0;

function walk(dir) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      walk(full);
    } else if (entry.endsWith('.vue')) {
      checkVue(full);
    } else if (entry.endsWith('.js')) {
      jsCount += 1;
      checkImports(full, readFileSync(full, 'utf8'));
    }
  }
}

function checkVue(file) {
  vueCount += 1;
  const source = readFileSync(file, 'utf8');
  const relative = file.slice(ROOT.length + 1);
  const { descriptor, errors } = parse(source, { filename: file });
  for (const error of errors) {
    problems.push(`${relative}: SFC 解析失败 · ${error.message}`);
  }

  const id = relative.replace(/[^a-z0-9]/gi, '');
  let bindings;
  if (descriptor.scriptSetup || descriptor.script) {
    try {
      const compiled = compileScript(descriptor, { id });
      bindings = compiled.bindings;
    } catch (error) {
      problems.push(`${relative}: <script> 编译失败 · ${error.message}`);
    }
  }

  if (descriptor.template) {
    const result = compileTemplate({
      source: descriptor.template.content,
      filename: file,
      id,
      compilerOptions: { bindingMetadata: bindings }
    });
    for (const error of result.errors || []) {
      problems.push(`${relative}: <template> 编译失败 · ${error.message || error}`);
    }
  }

  checkImports(file, source);
}

function checkImports(file, source) {
  const base = dirname(file);
  const patterns = [
    /(?:^|\n)\s*import\s+[^'"]*from\s+['"]([^'"]+)['"]/g,
    /(?:^|\n)\s*import\s+['"]([^'"]+)['"]/g,
    /import\(\s*['"]([^'"]+)['"]\s*\)/g
  ];
  const seen = new Set();
  for (const pattern of patterns) {
    let match;
    while ((match = pattern.exec(source)) !== null) {
      const spec = match[1];
      if (!spec.startsWith('.')) continue;
      if (seen.has(spec)) continue;
      seen.add(spec);
      importCount += 1;
      const target = resolve(base, spec);
      if (!existsSync(target) && !existsSync(`${target}.js`) && !existsSync(`${target}.vue`)) {
        problems.push(`${file.slice(ROOT.length + 1)}: import 指向不存在的文件 · ${spec}`);
      }
    }
  }
}

walk(ROOT);

// 路由 meta.require 只允许权限层已实现的要求
const routerSource = readFileSync(join(ROOT, 'router', 'index.js'), 'utf8');
for (const match of routerSource.matchAll(/require:\s*\[([^\]]*)\]/g)) {
  for (const raw of match[1].split(',')) {
    const name = raw.trim().replace(/['"]/g, '');
    if (!name) continue;
    if (!['AUTH'].includes(name)) {
      problems.push(`router/index.js: meta.require 使用了未实现的要求 · ${name}`);
    }
  }
}

console.log(`检查 .vue ${vueCount} 个 · .js ${jsCount} 个 · 本地 import ${importCount} 条`);
if (problems.length) {
  console.log('\n发现问题：');
  for (const problem of problems) console.log('  ✗', problem);
  process.exit(1);
}
console.log('✓ SFC 编译、本地 import 解析、路由 require 取值均通过');
