package org.jetbrains.kotlin.backend.common.overrides

import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrFakeOverrideFunctionImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFakeOverridePropertyImpl
import org.jetbrains.kotlin.ir.util.*

class FakeOverrideCopier(
    symbolRemapper: SymbolRemapper,
    private val typeRemapper: TypeRemapper,
    private val symbolRenamer: SymbolRenamer
) : DeepCopyIrTreeWithSymbols(symbolRemapper, typeRemapper, symbolRenamer) {

    private fun <T : IrFunction> T.transformFunctionChildren(declaration: T): T =
        apply {
            transformAnnotations(declaration)
            copyTypeParametersFrom(declaration)
            typeRemapper.withinScope(this) {
                // This is the more correct way to produce dispatch receiver for a fake override,
                // but some lowerings still expect the below behavior as produced by the current psi2ir.
                /*
                val superDispatchReceiver = declaration.dispatchReceiverParameter!!
                val dispatchReceiverSymbol = IrValueParameterSymbolImpl(WrappedReceiverParameterDescriptor())
                val dispatchReceiverType = destinationClass.defaultType
                dispatchReceiverParameter = IrValueParameterImpl(
                    superDispatchReceiver.startOffset,
                    superDispatchReceiver.endOffset,
                    superDispatchReceiver.origin,
                    dispatchReceiverSymbol,
                    superDispatchReceiver.name,
                    superDispatchReceiver.index,
                    dispatchReceiverType,
                    null,
                    superDispatchReceiver.isCrossinline,
                    superDispatchReceiver.isNoinline
                )
                */
                // Should fake override's receiver be the current class is an open question.
                dispatchReceiverParameter = declaration.dispatchReceiverParameter?.transform()
                extensionReceiverParameter = declaration.extensionReceiverParameter?.transform()
                returnType = typeRemapper.remapType(declaration.returnType)
                this.valueParameters = declaration.valueParameters.transform()
            }
        }

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrSimpleFunction =
        IrFakeOverrideFunctionImpl(
            declaration.startOffset, declaration.endOffset,
            IrDeclarationOrigin.FAKE_OVERRIDE,
            symbolRenamer.getFunctionName(declaration.symbol),
            declaration.visibility,
            declaration.modality,
            declaration.returnType,
            isInline = declaration.isInline,
            isExternal = false,
            isTailrec = declaration.isTailrec,
            isSuspend = declaration.isSuspend,
            isExpect = declaration.isExpect,
            isOperator = declaration.isOperator,
            isInfix = declaration.isInfix
        ).apply {
            transformFunctionChildren(declaration)
        }


    override fun visitProperty(declaration: IrProperty): IrProperty =
        IrFakeOverridePropertyImpl(
            declaration.startOffset, declaration.endOffset,
            IrDeclarationOrigin.FAKE_OVERRIDE,
            declaration.name,
            declaration.visibility,
            declaration.modality,
            isVar = declaration.isVar,
            isConst = declaration.isConst,
            isLateinit = declaration.isLateinit,
            isDelegated = declaration.isDelegated,
            isExpect = declaration.isExpect,
            isExternal = false
        ).apply {
            transformAnnotations(declaration)
            this.getter = declaration.getter?.transform()
            this.setter = declaration.setter?.transform()
        }
}
