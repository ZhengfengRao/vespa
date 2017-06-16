// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaRefeedAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates the indexing script changes in all fields in the current and next search model.
 *
 * @author geirst
 * @since 2014-12-08
 */
public class IndexingScriptChangeValidator {

    private final Search currentSearch;
    private final Search nextSearch;

    public IndexingScriptChangeValidator(Search currentSearch, Search nextSearch) {
        this.currentSearch = currentSearch;
        this.nextSearch = nextSearch;
    }

    public List<VespaConfigChangeAction> validate(ValidationOverrides overrides, Instant now) {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        for (SDField nextField : nextSearch.allConcreteFields()) {
            String fieldName = nextField.getName();
            SDField currentField = currentSearch.getConcreteField(fieldName);
            if (currentField != null) {
                validateScripts(currentField, nextField, overrides, now).ifPresent(r -> result.add(r));
            }
        }
        return result;
    }

    private Optional<VespaConfigChangeAction> validateScripts(SDField currentField, SDField nextField,
                                                              ValidationOverrides overrides, Instant now) {
        ScriptExpression currentScript = currentField.getIndexingScript();
        ScriptExpression nextScript = nextField.getIndexingScript();
        if ( ! equalScripts(currentScript, nextScript)) {
            ChangeMessageBuilder messageBuilder = new ChangeMessageBuilder(nextField.getName());
            new IndexingScriptChangeMessageBuilder(currentSearch, currentField, nextSearch, nextField).populate(messageBuilder);
            messageBuilder.addChange("indexing script", currentScript.toString(), nextScript.toString());
            return Optional.of(VespaRefeedAction.of(ValidationId.indexingChange.value(), overrides, messageBuilder.build(), now));
        }
        return Optional.empty();
    }

    static boolean equalScripts(ScriptExpression currentScript,
                                ScriptExpression nextScript) {
        // Output expressions are specifying in which context a field value is used (attribute, index, summary),
        // and do not affect how the field value is generated in the indexing doc proc.
        // The output expressions are therefore removed before doing the comparison.
        // Validating the addition / removal of attribute and index aspects are handled in other validators.
        return removeOutputExpressions(currentScript).equals(removeOutputExpressions(nextScript));
    }

    private static ScriptExpression removeOutputExpressions(ScriptExpression script) {
        ScriptExpression retval = (ScriptExpression) new OutputExpressionRemover().convert(script);
        return retval;
    }

    private static class OutputExpressionRemover extends ExpressionConverter {

        @Override
        protected boolean shouldConvert(Expression exp) {
            return exp instanceof OutputExpression;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            if (exp instanceof OutputExpression) {
                return null;
            }
            return exp;
        }
    }

}
