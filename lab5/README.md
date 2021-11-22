# [lab4](http://fit.ippolitov.me/CN_2/2021/5.html)

> SOCKS-прокси

# setup
1. open in idea
2. specify port in CLI arguments
3. click run

# benchmark
ok, tested using http://fit.ippolitov.me/gallery/ and some other resources like https://stackoverflow.com/

does not reduce download speed:
```bash
λ curl --socks5-hostname localhost:1080 http://mirror.yandex.ru/fedora/linux/releases/35/Kinoite/x86_64/os/isolinux/vmlinuz > /dev/null
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100 10.5M  100 10.5M    0     0   310k      0  0:00:34  0:00:34 --:--:--  338k
λ curl http://mirror.yandex.ru/fedora/linux/releases/35/Kinoite/x86_64/os/isolinux/vmlinuz > /dev/null
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100 10.5M  100 10.5M    0     0   342k      0  0:00:31  0:00:31 --:--:--  526k
```