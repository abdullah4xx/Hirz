package hirz.codx.app.data.db

import androidx.room.TypeConverter
import hirz.codx.app.data.model.ImportState
import hirz.codx.app.data.model.ItemType

class Converters {
    @TypeConverter fun fromItemType(value: ItemType): String = value.name
    @TypeConverter fun toItemType(value: String): ItemType = ItemType.valueOf(value)
    @TypeConverter fun fromImportState(value: ImportState): String = value.name
    @TypeConverter fun toImportState(value: String): ImportState = ImportState.valueOf(value)
}
