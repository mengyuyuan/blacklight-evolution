# NeoForge 1.21.1 收口审计：尚未通过的用例

2026-10-13。该文件保存失败事实，不把所有失败等同于已确认的游戏缺陷。定因和范围见 [审计报告](RELEASE_AUDIT_1.21.1.md)。

后续逐项原因、隔离复现和待验证边界见 [失败诊断](RELEASE_FAILURE_DIAGNOSIS_1.21.1.md)。以下原始失败列表保留，不以诊断替代完整复测。

## 单元测试（2 项）

- `com.codex.blacklight.resource.MvpResourceTest.sizedBladeKeepsHeadClearanceBetweenGrabKeys()`：org.opentest4j.AssertionFailedError: grab_blade interpolated clearance=0.0 at 0.0 ==> expected: <true> but was: <false>

- `com.codex.blacklight.resource.MvpResourceTest.newConsumeFormsKeepTheirOwnMeshesClearOfTheHead()`：org.opentest4j.AssertionFailedError: action/grab_blade R at 0.0 clearance=0.0; action/grab_blade_throw R at 0.62 clearance=0.0 ==> expected: <true> but was: <false>

## 完整服务端 GameTest（33 项）

| 用例 | 原始失败断言 |
| --- | --- |
| `everyformconsumespassiveandrewardsonce` | no early award in NONE |
| `consumptionclaimsandpaysonlyonce` | completed consumption removes victim and heals |
| `petsconstructsandmountedanimalsareexcluded` | deny pets, constructs and mounted pairs |
| `bladespecialusesdashoncefromstandingandsprintandwhipreachesbehind` | one hit per target in this Mod's dash policy |
| `committedspecialownsmovementuntilrecovery` | regular sprint must not add propulsion on top of action Coord |
| `whippullsownertoheavyvictimbeforeholding` | heavy target selects owner-pull branch |
| `survivalrecipeandguidanceareloaded` | virus sample survival recipe loads |
| `primaryandrepeatedgrabthrowatreleaseframewithoutrewards` | throw waits for hand release |
| `basichammerhascontactsplash` | splash must not double damage primary victim |
| `hammerkeepssmallvictimlifteduntilthedownstroke` | wind-up must not lower the body early |
| `adaptedmotionskeepsingleserverhitandrecovery` | one damage event for BLADE_CUT, got 1 |
| `healthyfarandinterruptedtargetsgivenoreward` | healthy hunter cannot be consumed |
| `normalenemyneedsweakeningandclaimsareexclusive` | healthy hostile rejected |
| `carryingcansprintturnandthrowwithoutstoppingorhittingvictim` | held state accepts actual sprint input and acceleration |
| `heavyswipehasoneserverimpact` | heavy swipe must deal one 7-point hit, without the old infection tick double-hit: 65.0 |
| `fixedhivefeedsoncewithoutmovingboss` | fixed western port must capture its nearby prey |
| `infectioncreatesoneindependentspecies` | must replace zombie exactly once |
| `biologicalgrowthcostsonceandmutantsunlockgenes` | vitality spends biomass and increases max health |
| `bladeimpaleswithrightarmthenconsumeswithoutdroppingvictim` | blade capture reaches a decision hold |
| `heldclicksdonotstartgroundspikeorclawattack` | held actions exclude combat and morph |
| `smallvictimsstayatpalmheightandfollowturns` | chicken must be held |
| `bladethrowreleasesonrightarmswingwithoutconsumerewards` | retain body before release |
| `airbornehammerwaitsforlandingandhitsonce` | landing contacts exactly once |
| `capturedoesnotpersistnoaianddamagerestoresmob` | damage frees cow immediately |
| `utilitypowerscoexistandresetwithnormalmode` | armour preserves weapon and adds defence |
| `whipgrabsattwelveblocksbutnotbeyondandreelsinbeforeconsuming` | beyond twelve blocks must be rejected |
| `heldtargetfollowswalkingandturning` | held body follows the rotated palm after walking; gap=1.6992995407737712 |
| `whipcapturessmallandlargebodiesattwelveblocks` | arrive at animated hand |
| `queuedcombolinksfromfollowthroughandkeepsbothfullhits` | queued strike deals full declared damage |
| `consumecannotgraborqueuewhilereaching` | early click cannot queue an automatic consume |
| `damageinterruptsandreloadrestoresai` | damage must cancel capture immediately |
| `allairspecialschasedistantmovingtargets` | acquire visible distant WHIPFIST 12 selected=-1 expected=753 range=128.0 |
| `allairopenerschasedistantmovingtargets` | acquire visible distant NONE 12 selected=-1 expected=755 range=128.0 |

来源：`build/release-audit-final-gametest.log`；109 项测试，76 通过、33 失败。新增的 3 项服务端审计回归在完整套件及独立运行中均通过。
