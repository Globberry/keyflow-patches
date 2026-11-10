package app.template.patches.keyflow

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.patch
import app.morphe.patcher.mutableClassDefBy
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.instruction.BranchInstruction

/**
 * Keyflow 1.5.10 — Pro Export Unlock.
 *
 * The f6olX class contains six premium-gate sites. Each has the shape:
 *
 *     invoke-virtual ..., Lv5YtyUtD;->yMQp6hm(...)Ljava/lang/Object;
 *     move-result-object vX
 *     if-ne vX, vY, :cond_Z        ; premium path (result != not-premium sentinel)
 *     goto :goto_W                  ; failure path (returns not-premium)
 *
 * Replacing every failure `goto :goto_W` with a jump to the premium `:cond_Z`
 * label makes every code path report Premium — including the export flow,
 * which uses one of the sites that a 3-site manual patch misses.
 */
val COMPATIBILITY_KEYFLOW = Compatibility(
    name = "Keyflow",
    packageName = "com.angkin.keyflow",
    apkFileType = ApkFileType.APK,
    appIconColor = 0xFF6B00,
    targets = listOf(
        AppTarget(version = "1.5.10")
    )
)

val keyflowProExportPatch = patch(
    name = "Keyflow Pro Export Unlock",
    description = "Bypasses all six f6olX premium-gate checks so Premium features (including export) work in Keyflow 1.5.10.",
    default = true
) {
    compatibleWith(COMPATIBILITY_KEYFLOW)

    execute {
        val f6olX = mutableClassDefBy("Lf6olX;")
        var patched = 0
        for (method in f6olX.methods) {
            val impl = method.implementation ?: continue
            val instrs = impl.instructions
            for (i in instrs.indices) {
                val instr = instrs[i]
                if (instr.opcode == Opcode.IF_NE && i + 1 < instrs.size) {
                    val next = instrs[i + 1]
                    if (next.opcode == Opcode.GOTO) {
                        val condLabel = (instr as BranchInstruction).branchTargets?.firstOrNull() ?: continue
                        val gotoLabel = (next as BranchInstruction).branchTargets?.firstOrNull() ?: continue
                        // Only bypass when the if-ne jumps to the premium :cond_Z label
                        // but the goto jumps elsewhere (the failure :goto_W path).
                        if (condLabel != gotoLabel) {
                            (next as BranchInstruction).branchTargets = arrayOf(condLabel)
                            patched++
                        }
                    }
                }
            }
        }
        if (patched == 0) {
            throw Error(
                "Keyflow Pro Export Unlock: no f6olX premium-gate goto found — expected Keyflow 1.5.10. " +
                "Patched $patched site(s)."
            )
        }
    }
}
