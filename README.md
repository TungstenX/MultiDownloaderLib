
[![Build Status](https://travis-ci.org/TungstenX/MultiDownloaderLib.svg?branch=master)](https://travis-ci.org/TungstenX/MultiDownloaderLib)

Find me on [stackoverflow](http://stackoverflow.com/users/537566/tungstenx)

# MultiDownloaderLib
Java Library to do multi-threaded downloading

## Constructors

There are three constructors available, no default constructor:
- MultiDownloader(URL url, int parts, int bufferSize)
- MultiDownloader(URL url, String path, int parts, int bufferSize)
- MultiDownloader(URL url, StringBuilder path, int parts, int bufferSize)

Where:
- URL url - you'll never guess; it is the URL of the file to be downloaded
- String / StringBuilder path - The path for the downloaded file (and interim parts), should just be the directory, default is the current working directory (First constructor)
- int parts - the number of thread to use / part downloads - a good number is 4 
- int bufferSize - The buffer size for the concatenating read / write, a good number is 8192

### Powered by
This README.md was made using VIM

[![Andr&#233; Labuschagn&#233;](http://gravatar.com/avatar/88ebc726d33c8ddba2534d1d6f93e638?s=144)](https://www.ParanoidAndroid.co.za) |
---|
[Andr&#233; Labuschagn&#233;](https://www.ParanoidAndroid.co.za) | 

