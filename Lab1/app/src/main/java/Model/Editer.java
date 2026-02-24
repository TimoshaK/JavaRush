package Model;

import Interfaces.IEditer;
import Interfaces.IReader;
import Interfaces.ISaver;

public class Editer // implements IEditer
{
    /*
    работаем по интерфейсам
    IReader - буфер файлов. Данные хранятся и изменяются в классе с помощью методов IReader.
    Конструктор открывает любой файл и записывает байты в свою память.
    Один объект Reader - один файл. (!Еще  не определился)
     */
    private IReader ReadFile1, ReadFile2;
    /*
    Должен сохранять объект IReader
    Можно было бы потом объеденить Saver с Reader.
     */
    private ISaver Saver;//+++++++
}
