package com.dsmod.probe;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.util.List;

final class DexKitResolver {

    private static final String CHAT_REQUEST_DESCRIPTOR =
            "com.deepseek.chat.network.chat.model.chat.ChatFullCompletionRequest";

    private DexKitResolver() {}

    static void resolve(ClassLoader cl) {
        Targets.chatRequest = null;
        if (!loadDexkit()) {
            return;
        }
        try (DexKitBridge bridge = DexKitBridge.create(cl, true)) {
            ClassDataList serializers = bridge.findClass(new FindClass().matcher(new ClassMatcher()
                    .usingEqStrings(CHAT_REQUEST_DESCRIPTOR, "chat_session_id", "parent_message_id", "prompt")));
            if (serializers.size() != 1) {
                Main.log("dexkit chatRequest serializer count=" + serializers.size());
                return;
            }

            ClassData serializer = serializers.get(0);
            MethodDataList readers = serializer.findMethod(new FindMethod().matcher(new MethodMatcher()
                    .returnType(Object.class).paramCount(1)));
            if (readers.size() != 1) {
                Main.log("dexkit chatRequest reader count=" + readers.size());
                return;
            }

            MethodData constructor = null;
            int constructorCount = 0;
            for (MethodData invoked : readers.get(0).getInvokes()) {
                if (!isChatRequestSerializationConstructor(invoked)) {
                    continue;
                }
                constructor = invoked;
                constructorCount++;
            }
            if (constructorCount != 1) {
                Main.log("dexkit chatRequest target count=" + constructorCount);
                return;
            }

            ClassData requestClass = constructor.getDeclaredClass();
            if (requestClass == null) {
                Main.log("dexkit chatRequest target class missing");
                return;
            }
            Targets.chatRequest = requestClass.getInstance(cl);
            Main.log("dexkit chatRequest=" + Targets.chatRequest.getName() + " serializer=" + serializer.getName());
        } catch (Throwable t) {
            Main.log("dexkit resolve failed: " + t);
        }
    }

    private static boolean isChatRequestSerializationConstructor(MethodData method) {
        if (!method.isConstructor()) {
            return false;
        }
        List<String> params = method.getParamTypeNames();
        return params.size() >= 4 && "int".equals(params.get(0)) && "java.lang.String".equals(params.get(1))
                && "java.lang.Integer".equals(params.get(2)) && "java.lang.String".equals(params.get(3));
    }

    private static boolean loadDexkit() {
        try {
            System.loadLibrary("dexkit");
            return true;
        } catch (UnsatisfiedLinkError e) {
            Main.log("libdexkit.so failed to load: " + e);
            return false;
        }
    }
}
