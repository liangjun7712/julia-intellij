package org.ice1000.julia.lang.module

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ComboboxWithBrowseButton
import org.ice1000.julia.lang.*
import org.ice1000.julia.lang.editing.JULIA_BIG_ICON
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import javax.swing.DefaultComboBoxModel
import javax.swing.JList

class JuliaSdkType : SdkType(JuliaBundle.message("julia.name")) {
	override fun getPresentableName() = JuliaBundle.message("julia.modules.sdk.name")
	override fun getIcon() = JULIA_BIG_ICON
	override fun getIconForAddAction() = icon
	override fun isValidSdkHome(sdkHome: String?) = validateJuliaSDK(sdkHome.orEmpty())
	override fun suggestSdkName(s: String?, p1: String?) = JuliaBundle.message("julia.modules.sdk.name")
	override fun suggestHomePath() = defaultSdkHome
	override fun getDownloadSdkUrl() = JULIA_WEBSITE
	override fun createAdditionalDataConfigurable(md: SdkModel, m: SdkModificator) = JuliaSdkDataConfigurable()
	override fun getVersionString(sdkHome: String?) = versionOf(sdkHome.orEmpty())
	override fun saveAdditionalData(additionalData: SdkAdditionalData, element: Element) = Unit // leave blank
	override fun setupSdkPaths(sdk: Sdk, sdkModel: SdkModel): Boolean {
		val modificator = sdk.sdkModificator
		modificator.sdkAdditionalData = sdk.sdkAdditionalData ?: JuliaSdkData(importPath = "")
		modificator.versionString = getVersionString(sdk) ?: JuliaBundle.message("julia.modules.sdk.unknown-version")
		modificator.commitChanges()
		return true
	}

	companion object InstanceHolder {
		val instance get() = SdkType.findInstance(JuliaSdkType::class.java)
	}
}

val defaultSdkHome by lazy {
	val existPath = PropertiesComponent.getInstance().getValue(JULIA_SDK_HOME_PATH_ID).orEmpty()
	when {
		validateJuliaSDK(existPath) -> existPath
		SystemInfo.isWindows -> findPathWindows() ?: "C:\\Program Files"
		SystemInfo.isMac -> findPathMac()
		else -> findPathLinux() ?: "/usr/share/julia"
	}
}

fun findPathMac(): String {
	val appPath = Paths.get(MAC_APPLICATIONS)
	val result = Files.list(appPath).collect(Collectors.toList()).firstOrNull { application ->
		application.toString().contains("julia", true)
	} ?: appPath
	val folderAfterPath = "/Contents/Resources/julia/bin/julia"
	return result.toAbsolutePath().toString() + folderAfterPath
}

fun findPathWindows() = executeCommandToFindPath("where julia")
private fun findPathLinux() = executeCommandToFindPath("whereis julia")

fun SdkAdditionalData?.toJuliaSdkData() = this as? JuliaSdkData

open class JuliaSdkData(
	var tryEvaluateTimeLimit: Long = 2500L,
	var tryEvaluateTextLimit: Int = 320,
	var importPath: String) : SdkAdditionalData {
	override fun clone() = JuliaSdkData(tryEvaluateTimeLimit, tryEvaluateTextLimit, "")
}

fun versionOf(exePath: String, timeLimit: Long = 500L) =
	executeJulia(exePath, null, timeLimit, "--version")
		.first
		.firstOrNull { it.startsWith("julia version", true) }
		?.dropWhile { it.isLetter() or it.isWhitespace() }
		?: JuliaBundle.message("julia.modules.sdk.unknown-version")

fun importPathOf(exePath: String, timeLimit: Long = 500L) =
	executeJulia(exePath, null, timeLimit, "--print", "\"Pkg.dir()\"")
		.first
		.firstOrNull { Files.isDirectory(Paths.get(it)) }
		?: Paths.get(exePath).parent.parent.toString()

fun validateJuliaSDK(exePath: String) = versionOf(exePath) != JuliaBundle.message("julia.modules.sdk.unknown-version")

class JuliaSdkComboBox : ComboboxWithBrowseButton() {
	val selectedSdk get() = comboBox.selectedItem as? Sdk
	val sdkName get() = selectedSdk?.name.orEmpty()

	init {
		comboBox.setRenderer(object : ColoredListCellRenderer<Sdk?>() {
			override fun customizeCellRenderer(
				list: JList<out Sdk?>,
				value: Sdk?,
				index: Int,
				selected: Boolean,
				hasFocus: Boolean) {
				value?.name?.let(::append)
			}
		})
		addActionListener {
			var selectedSdk = selectedSdk
			val project = ProjectManager.getInstance().defaultProject
			val editor = ProjectJdksEditor(selectedSdk, project, this@JuliaSdkComboBox)
			editor.title = JuliaBundle.message("julia.modules.sdk.selection.title")
			editor.show()
			if (editor.isOK) {
				selectedSdk = editor.selectedJdk
				updateSdkList(selectedSdk)
			}
		}
		updateSdkList()
	}

	private fun updateSdkList(sdkToSelectOuter: Sdk? = null) {
		ProjectJdkTable.getInstance().getSdksOfType(JuliaSdkType.instance).run {
			comboBox.model = DefaultComboBoxModel(toTypedArray())
			comboBox.selectedItem = sdkToSelectOuter ?: firstOrNull()
		}
	}
}