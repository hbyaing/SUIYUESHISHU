param(
 [Parameter(Mandatory=$true)][string]$MinecraftJar,
 [Parameter(Mandatory=$true)][string]$Javap
)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$targets=@{
 'VitalDataItemGuard'='net.minecraft.network.syncher.SynchedEntityData$DataItem'
 'ForgeDefenseGuard'='net.minecraftforge.common.ForgeHooks'
 'EntitySectionGuard'='net.minecraft.world.level.entity.EntitySection'
 'LookupAnchor'='net.minecraft.world.level.entity.EntityLookup'
 'TickListAnchor'='net.minecraft.world.level.entity.EntityTickList'
 'CharityItem'='net.minecraft.world.entity.item.ItemEntity'
 'CharityStackMigration'='net.minecraft.world.item.ItemStack'
 'TemperanceStack'='net.minecraft.world.item.ItemStack'
 'TemperancePlayer'='net.minecraft.world.entity.player.Player'
 'TemperanceFood'='net.minecraft.world.food.FoodData'
 'TemperanceMining'='net.minecraft.server.level.ServerPlayerGameMode'
 'CreativeUnequip'='net.minecraft.server.network.ServerGamePacketListenerImpl'
 'InventoryAnchor'='net.minecraft.world.entity.player.Inventory'
 'LivingGuard'='net.minecraft.world.entity.LivingEntity'
 'PlayerGuard'='net.minecraft.world.entity.player.Player'
 'ServerPlayerGuard'='net.minecraft.server.level.ServerPlayer'
 'VitalDataGuard'='net.minecraft.network.syncher.SynchedEntityData'
 'EntityGuard'='net.minecraft.world.entity.Entity'
 'ServerLevelGuard'='net.minecraft.server.level.ServerLevel'
 'MobStillness'='net.minecraft.world.entity.Mob'
 'HumilityVibration'=@('net.minecraft.world.level.block.entity.SculkSensorBlockEntity$VibrationUser','net.minecraft.world.level.block.entity.SculkShriekerBlockEntity$VibrationUser','net.minecraft.world.entity.monster.warden.Warden$VibrationUser')
 'HumilityLiving'='net.minecraft.world.entity.LivingEntity'
 'HumilityPlayerDamage'=@('net.minecraft.world.entity.player.Player','net.minecraft.server.level.ServerPlayer')
 'HumilityTargeting'='net.minecraft.world.entity.ai.targeting.TargetingConditions'
 'HumilitySensing'='net.minecraft.world.entity.ai.sensing.Sensing'
 'HumilityBrain'='net.minecraft.world.entity.ai.Brain'
 'HumilityShrieker'='net.minecraft.world.level.block.entity.SculkShriekerBlockEntity'
 'HumilityDragon'='net.minecraft.world.entity.boss.enderdragon.phases.DragonStrafePlayerPhase'
 'HumilityWither'='net.minecraft.world.entity.boss.wither.WitherBoss'
 'HumilityPlayerRenderer'='net.minecraft.client.renderer.entity.player.PlayerRenderer'
}
$checked=0
foreach($mixin in $targets.Keys){
 foreach($target in @($targets[$mixin])){
 $dump=& $Javap -classpath $MinecraftJar -p -s $target
 if($LASTEXITCODE -ne 0){throw "javap failed: $target"}
 $methods=@();$methodName=$null
 foreach($line in $dump){
  if($line -match '\s([^\s(]+)\([^)]*\).*;\s*$'){
   $methodName=$Matches[1]
   if($methodName -eq $target){$methodName='<init>'}
  }elseif($line -match '^\s+descriptor: (\(.*)$'){
   if($null -ne $methodName){$methods+=($methodName+$Matches[1])}
   $methodName=$null
  }
 }
 $source=[IO.File]::ReadAllText((Join-Path $projectRoot "src/main/java/dev/starcrown/mixin/$mixin.java"))
 $injections=[regex]::Matches($source,'(?ms)^[ \t]*@(?:Inject|ModifyVariable)\s*\(\s*method\s*=\s*(\{[^}]+\}|"[^"]+")(.*?)\)\s*(?=\r?\n[ \t]*(?:private|public|protected)\b)')
 foreach($injection in $injections){
  if($injection.Groups[2].Value -notmatch '\brequire\s*=\s*([1-9][0-9]*)') { continue }
  $required=[int]$Matches[1]
  $selectors=[regex]::Matches($injection.Groups[1].Value,'"([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
  $found=@($methods | Where-Object { $signature=$_; @($selectors | Where-Object { if($_.Contains('(')){$signature -eq $_}else{$signature.StartsWith($_+'(')} }).Count -gt 0 })
  if($found.Count -lt $required){throw "Missing required hook in ${target}: $($selectors -join ', ')"}
  $checked++
 }
 }
}
if($checked -eq 0){throw 'No required hook groups found'}
Write-Output "PASS: $checked required hooks match declared method descriptors in $MinecraftJar"
