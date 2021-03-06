package org.jetlinks.rule.engine.executor;

import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.api.executor.StreamRuleNode;
import org.jetlinks.rule.engine.executor.supports.RuleNodeConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public abstract class AbstractExecutableRuleNodeFactoryStrategy<C extends RuleNodeConfig>
        implements ExecutableRuleNodeFactoryStrategy {

    public abstract C newConfig();

    public abstract String getSupportType();

    public abstract BiFunction<Logger, Object, CompletionStage<Object>> createExecutor(C config);

    public ExecutableRuleNode create(C config) {
        BiFunction<Logger, Object, CompletionStage<Object>> executor = createExecutor(config);
        return context -> {
            Object data = context.getData();
            CompletionStage<Object> stage = executor.apply(context.logger(), data);
            if (config.getNodeType().isReturnNewValue()) {
                return stage;
            } else {
                CompletableFuture<Object> real = new CompletableFuture<>();
                stage.whenComplete((result, error) -> {
                    if (error != null) {
                        real.completeExceptionally(error);
                    } else {
                        real.complete(data);
                    }
                });
                return real;
            }
        };
    }

    public StreamRuleNode createStream(C config) {
        BiFunction<Logger, Object, CompletionStage<Object>> executor = createExecutor(config);
        return context -> context.getInput()
                .acceptOnce(data ->
                        executor.apply(context.logger(), data.getData())
                                .whenComplete((result, error) -> {
                                    if (error != null) {
                                        context.onError(data, error);
                                    } else {
                                        //如果类型是MAP则返回新的值
                                        if (config.getNodeType().isReturnNewValue()) {
                                            context.getOutput()
                                                    .write(data.newData(result));
                                        } else {
                                            context.getOutput()
                                                    .write(data);
                                        }
                                    }
                                }));
    }

    @Override
    public StreamRuleNode createStream(RuleNodeConfiguration configuration) {
        return createStream(convertConfig(configuration));
    }

    @Override
    public ExecutableRuleNode create(RuleNodeConfiguration configuration) {
        return create(convertConfig(configuration));
    }

    public C convertConfig(RuleNodeConfiguration configuration) {
        C config = FastBeanCopier.copy(configuration.getConfiguration(), this::newConfig);
        config.setNodeType(configuration.getNodeType());
        return config;
    }
}
