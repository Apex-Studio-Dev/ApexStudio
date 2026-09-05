package dev.apexstudio.ide.layouteditor.editor.palette.text

import android.content.Context
import android.graphics.Canvas
import com.google.android.material.textfield.TextInputEditText
import dev.apexstudio.ide.layouteditor.utils.Constants
import dev.apexstudio.ide.layouteditor.utils.Utils

open class TextInputEditTextDesign(context: Context) : TextInputEditText(context) {

    private var drawStrokeEnabled: Boolean = false
    private var isBlueprint: Boolean = false

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        if (drawStrokeEnabled) {
            Utils.drawDashPathStroke(
                this, 
                canvas, 
                if (isBlueprint) Constants.BLUEPRINT_DASH_COLOR else Constants.DESIGN_DASH_COLOR
            )
        }
    }

    fun setStrokeEnabled(enabled: Boolean) {
        drawStrokeEnabled = enabled
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        if (isBlueprint) {
            Utils.drawDashPathStroke(this, canvas, Constants.BLUEPRINT_DASH_COLOR)
        } else {
            super.draw(canvas)
        }
    }

    fun setBlueprint(isBlueprint: Boolean) {
        this.isBlueprint = isBlueprint
        invalidate()
    }
}
