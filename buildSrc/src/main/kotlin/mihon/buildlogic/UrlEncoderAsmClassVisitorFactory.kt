package mihon.buildlogic

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Rewrites NewPipeExtractor's calls to URLEncoder.encode(String, Charset) / URLDecoder.decode(String,
 * Charset) — which only exist on API 33+ — to a compat helper that works on older Android. This lets
 * the latest NewPipeExtractor run on Android 9 (otherwise it throws NoSuchMethodError at runtime).
 */
abstract class UrlEncoderAsmClassVisitorFactory :
    AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor = object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor {
            val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            return object : MethodVisitor(Opcodes.ASM9, mv) {
                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String?,
                    methodName: String?,
                    methodDescriptor: String?,
                    isInterface: Boolean,
                ) {
                    val charsetDesc = "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;"
                    if (opcode == Opcodes.INVOKESTATIC && methodDescriptor == charsetDesc &&
                        owner == "java/net/URLEncoder" && methodName == "encode"
                    ) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT, "encode", charsetDesc, false)
                    } else if (opcode == Opcodes.INVOKESTATIC && methodDescriptor == charsetDesc &&
                        owner == "java/net/URLDecoder" && methodName == "decode"
                    ) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT, "decode", charsetDesc, false)
                    } else {
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface)
                    }
                }
            }
        }
    }

    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.className.startsWith("org.schabi.newpipe")

    companion object {
        private const val COMPAT = "eu/kanade/tachiyomi/util/UrlCodecCompat"
    }
}
