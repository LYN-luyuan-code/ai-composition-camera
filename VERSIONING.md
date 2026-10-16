# 本地版本管理

`main` 保存项目基线，`v0.1.0` 标记首个版本。相机界面重写在 `codex/camera-reference-ui` 分支，版本为 `v0.2.0`。当前只使用本地 Git，没有配置远程仓库；电脑损坏或目录丢失时，本地 Git 也会丢失，需要另行备份项目。

源码、测试、Gradle Wrapper、产品与设计说明纳入版本管理。构建输出、APK、模拟器、界面检查截图、本机 SDK 路径及密钥文件由 `.gitignore` 排除，原文件仍留在本地。

## 日常修改

开始一个功能时创建分支，例如：

```bash
git switch -c codex/next-feature
```

完成一个有意义的改动后检查差异，只添加相关文件并提交：

```bash
git status
git diff
git add <修改的文件>
git diff --cached
git commit -m "feat: 描述本次功能"
```

提交说明建议使用 `feat:`（功能）、`fix:`（修复）、`docs:`（文档）或 `chore:`（维护）。功能验证完成后合回主分支：

```bash
git switch main
git merge --no-ff codex/next-feature
```

## 发布新版本

1. 修改 `app/build.gradle.kts` 的 `versionName`，每次分发新的安装版本时递增 `versionCode`。
2. 在 `CHANGELOG.md` 记录变化和验证范围。
3. 运行 `./gradlew testDebugUnitTest assembleDebug lintDebug`，提交后再建立带注释的版本标签。

例如下次修复版使用 `versionName = "0.2.1"`、`versionCode = 3`，对应标签：

```bash
git tag -a v0.2.1 -m "Release 0.2.1"
```

已建立的版本标签保留原指向，修复用新提交和新版本记录。

## 查看和回退

```bash
git log --oneline --decorate --graph --all
git show v0.1.0
git diff v0.1.0..HEAD
```

撤销已提交的改动使用 `git revert <提交号>`，它会新增一条撤销记录，保留历史。要查看旧版，可从标签创建独立分支：`git switch -c codex/check-v0.1.0 v0.1.0`。切换前先提交或暂存当前修改。

提交署名仅在本仓库配置为 `Local Developer <local@localhost>`。以后可通过 `git config --local user.name "你的署名"` 和 `git config --local user.email "你的邮箱"` 更改。
