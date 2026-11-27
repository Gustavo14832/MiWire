# 🎮 MiWire

<div align="center">

<img src="docs/images/title.svg" alt="MiWire" width="380" />

**Cliente de streaming Moonlight hiper-calibrado para a Xiaomi Mi Box S (3ª Geração) a 1080p @ 60 Hz.**

*Fork especializado do [Starwire](https://github.com/SicarioGaming/Starwire) (por SicarioGaming) e do [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) (por Cameron Gutman).*

[![Licença: GPL-3.0](https://img.shields.io/badge/licen%C3%A7a-GPL--3.0-FF6700?style=flat-square)](LICENSE.txt)
[![Android TV 11+](https://img.shields.io/badge/Android%20TV-11%2B%20(Google%20TV)-34A853?style=flat-square&logo=android&logoColor=white)](https://github.com/Gustavo14832/MiWire/releases/latest)
[![Target: Mi Box S Gen 3](https://img.shields.io/badge/Target-Mi%20Box%20S%20Gen%203-FF6700?style=flat-square)](https://github.com/Gustavo14832/MiWire/releases/latest)
[![Package: com.miwire](https://img.shields.io/badge/package-com.miwire-blue?style=flat-square)](https://github.com/Gustavo14832/MiWire/releases/latest)

### [Baixar MiWire 0.4.0](https://github.com/Gustavo14832/MiWire/releases/tag/v0.4.0) · [Backup v320](https://github.com/Gustavo14832/MiWire/releases/tag/v0.3.0-miware)

**Versão Estável (Backup):** `0.3.0` (`v0.3.0-miware` · `com.miware`) | **SHA-256:** `572CEBCEB5D47D410091CDBF2088762E2AE523C93A3D7DE0D5D42F7F1CF975FF`  
**Atualização da Box:** o APK MiWire Box ARMv7a atualiza a v325 local. O APK MiWire universal atualiza a edição oficial com pacote com.miwire.

</div>

---

> O perfil de referência usa a Xiaomi Mi Box S de 3ª geração, saída Full HD a 60 Hz e stream a 60 fps. O monitor, a cadência do host, a rede e os recursos expostos pelo driver determinam os intervalos reais de apresentação. Outros aparelhos podem escolher os demais modos de frame pacing.

---

## 🌟 O que é o MiWire?

O **MiWire** é um fork independente derivado do projeto original **[Starwire](https://github.com/SicarioGaming/Starwire)** (criado por *SicarioGaming*), construído sobre o **[Moonlight Android](https://github.com/moonlight-stream/moonlight-android)** (criado por *Cameron Gutman*) e reunindo recursos de outros clientes da comunidade Moonlight.

Ele nasceu com um propósito específico: transformar a **Xiaomi Mi Box S Gen 3** em um console de streaming de baixíssima latência para jogos de PC na rede local ou na nuvem (como instâncias GPU usando **Sunshine**, **Vibepollo** ou **Apollo**).

---

## 🚀 Recursos Exclusivos do MiWire (Otimizações Mi Box S Gen 3)

### 1. 👻 Ghost Overlay na TV
* Com as estatísticas ocultas, o aplicativo solicita uma atualização discreta da interface a cada segundo durante a sessão. O efeito sobre os intervalos de apresentação depende do compositor e da saída HDMI. A seção 12 do histórico descreve como comparar o comportamento na Box.

### 2. ⏱️ Frame Pacing `mibox-60hz` & Renderizador Vulkan Avançado
* **Pacing dedicado (`FRAME_PACING_MIBOX_60HZ`).** O perfil usa um ciclo de tela por quadro e permite até 10 ms de retenção adicional para absorver variações de chegada. Com a extensão de temporização Vulkan, o escalonador ajusta a antecedência a partir dos horários informados pelo driver.
* **Nitidez Vulkan RCAS + 10-bit SDR:** Filtro AMD FidelityFX Contrast-Adaptive Sharpening (`Off`, `Balanced`, `Crisp`) via shader GLSL/SPIR-V com gate de ruído, reconstrução explícita de croma e *Spatial Dithering* em swapchain 10-bit SDR.
* **Telemetria de apresentação.** No perfil MiBox com a extensão de temporização Vulkan, o painel detalhado mostra p50/p95/p99 e 1% low calculados sobre intervalos entre apresentações informadas pelo driver, incluindo pausas. Os intervalos longos dos últimos 60 segundos ajudam a localizar engasgos raros.

### 3. 🎯 Calibração Automática Out-of-the-Box para Android TV
Na primeira execução em dispositivos Android TV / Amlogic (`isTvOrAmlogicDevice`), o MiWire configura automaticamente:
* **Vídeo:** `1920x1080` @ `60 FPS`, `35 Mbps`, **HEVC (H.265)**, renderizador **Vulkan**, `mibox-60hz`, dither `high`, RCAS `balanced`, Full Range (`0-255`), `exactSlices` desativado (preservando predição NVENC de quadro inteiro).
* **Decodificação de vídeo.** H.264, HEVC e AV1 dependem dos decodificadores anunciados pelo Android e do host. O cliente tenta opções de baixa latência compatíveis com o fabricante e reduz essas opções nas tentativas de recuperação. O monitor de travamento pode solicitar quadro-chave, limpar buffers e reiniciar o decoder.
* **Sistema, Faixa Dinâmica & Áudio:** ADPF (`PerformanceHintManager` em Java e C++) ativo sob demanda (com Modo de Desempenho Sustentado opcional desativado por padrão na TV), prioridade elevada de threads de renderização/áudio, `AudioTrack` de baixa latência no HDMI em Android 12+, fila máxima de áudio de `20 ms`, skip de Wi-Fi lock na rede Ethernet, compatibilidade opt-in com `HDR10` / `HDR10+` / `HLG` / `Dolby Vision` (incluindo mapeamento Vulkan 10-bit PQ→SDR), negociação automática HDMI/AVR (`auto`) e opção de áudio `PCM Float 32-bit`.
* **Casca e Menus Leves (fora do streaming):** reciclagem com `ViewHolder`, cache leve de preferências por jogo, seleção via D-pad sem recarregar toda a grade, decodificação escalonada de capas e limpeza imediata de executores ao sair das telas.
* **Interface e controles na TV.** Preto OLED (#000000), foco âmbar (#FF963E), biblioteca navegável pelo direcional e HUD de sessão. No perfil da Box, LB+RB abre o menu e o botão Back pode atuar como Meta.

### 4. 🖱️ Mouse e periféricos
* **Acumulador Sub-pixel:** Deltas fracionários de mouse relativos são acumulados em vez de descartados, preservando movimentos lentos que seriam arredondados para zero.
* **Movimento relativo:** o cliente envia o deslocamento disponível assim que a fila permite. Só soma movimentos consecutivos ainda na fila, preservando a posição de cada clique. A frequência de entrada depende do mouse e do Android.
* **Rede:** drenagem de pacotes UDP em rajada via `recvmmsg`, marcação QoS DSCP (EF áudio / CS4 vídeo) e `PR_SET_TIMERSLACK = 1 ns`.
* **Controles e Teclado:** Deduplicação de pacotes repetidos de controle (com heartbeat de 1 s), leitura dos deslocamentos de centro de analógico já salvos, serviço de acessibilidade para teclado físico (Alt+Tab, Win), suporte a teclas extras **ABNT2/ISO** e correção de `Print Screen`.

---

## 🛠️ Recursos Herdados do Ecossistema Starwire & Moonlight

Além das otimizações exclusivas para Mi Box S Gen 3, o MiWire preserva os recursos modernos trazidos pelo **Starwire** e demais forks:

* **ID de Pacote Próprio (`com.miwire` / `com.miwire.debug`):** Instala lado a lado com Moonlight oficial e Starwire (e com o backup legado `com.miware`), mantendo assinatura fixa em todas as novas builds para atualizar sem desinstalar.
* **Gatilhos Adaptativos DualSense (Experimental):** Efeitos de gatilho via Bluetooth (Android 12+) com suporte a hosts compatíveis (como Vibepollo) ou presets integrados.
* **Passthrough USB Completo (Experimental):** Compartilhe volantes, controles e dispositivos USB com o PC via túnel USB/IP criptografado e auxiliar Windows ([guia Windows USB](windows/USB-HELPER.md)).
* **Microfone Criptografado para Qualquer Host:** Envie seu microfone via protocolo criptografado SWMC v1 para o receptor Windows ([guia Microfone Windows](windows/README.md)), VBAN ou diretamente embutido no stream (Apollo).
* **Menu In-Stream Completo:** Modo toque/trackpad/multi-touch, teclado completo de 82 teclas na tela, atalhos importáveis em JSON, alternador de monitores, ajuste de bitrate em tempo real, sincronização de área de transferência, Virtual Display e comandos de servidor.

---

## 📥 Como Instalar e Usar na Mi Box S Gen 3

1. Na [release](https://github.com/Gustavo14832/MiWire/releases/tag/v0.4.0), escolha **MiWire-Box-v326-armeabi-v7a.apk** para atualizar o MiWire Box v325 ou **MiWire-0.4.0.apk** para atualizar o MiWire oficial.
2. Confira o SHA-256 indicado ao lado do arquivo no GitHub.
3. Transfira o APK para a Mi Box via pendrive ou *Send Files to TV* e instale usando um gerenciador de arquivos (como *X-plore* ou *AnExplorer*).
4. Abra o **MiWire**, pareie com seu PC (**Sunshine**, **Vibepollo** ou **Apollo**) e selecione o perfil Mi Box 1080p60.
5. *(Opcional)* Para usar microfone ou passthrough USB com um PC Windows, confira os scripts e instruções na pasta [`windows/`](windows/README.md).

---

## 📚 Documentação e Repositório

* **Contexto Técnico & Handoff para IA:** consulte [`docs/HANDOFF_MIWIRE.md`](docs/HANDOFF_MIWIRE.md) para o histórico completo de calibração, hardware alvo, regras de assinatura e decisões de engenharia.
* **Código-fonte e Build:** consulte [`CONTRIBUTING.md`](CONTRIBUTING.md) para instruções de compilação com Gradle/NDK e execução de testes.
* **Histórico de Versões:** consulte [`CHANGELOG.md`](CHANGELOG.md) e a [página de Releases](https://github.com/Gustavo14832/MiWire/releases).
* **Segurança:** consulte [`SECURITY.md`](SECURITY.md) para detalhes sobre criptografia, drivers customizados e relato de vulnerabilidades.
* **Proveniência e Binários:** consulte [`docs/PROVENANCE.md`](docs/PROVENANCE.md).
* **Reportar Problemas / Sugestões:** abra uma [Issue](https://github.com/Gustavo14832/MiWire/issues).

---

## ⚖️ Conformidade Legal, Licenciamento e Créditos

Todo o código-fonte do **MiWire** é software livre licenciado sob a **GNU General Public License v3.0 (GPL-3.0)** — veja o texto integral em [`LICENSE.txt`](LICENSE.txt).

### Créditos ao Projeto Original e Projetos da Comunidade

O MiWire existe graças ao trabalho dos desenvolvedores originais do **Starwire**, do **Moonlight** e de toda a comunidade open source:

| Projeto | Autor Original / Mantenedor | Licença | Contribuição Utilizada |
|---|---|---|---|
| **[Starwire](https://github.com/SicarioGaming/Starwire)** *(APK original do fork)* | SicarioGaming | GPL-3.0 | Base direta do fork, interface Cover Glow, microfone criptografado (SWMC v1), passthrough USB/IP, DualSense Bluetooth, pipeline de testes e infraestrutura CI. |
| **[Moonlight Android](https://github.com/moonlight-stream/moonlight-android)** | Cameron Gutman (cgutman) | GPL-3.0 | Cliente Moonlight Android fundamental, arquitetura de streaming, áudio, input e decodificação. |
| **[Moonlight X](https://github.com/MoreOrLessSoftware/moonlight-android)** | MoreOrLessSoftware | GPL-3.0 | Base upstream do Starwire: renderizador Vulkan, PyroWave, menu overlay, configurações por jogo, Quick Launch e resoluções/FPS customizados. |
| **[Artemis](https://github.com/ClassicOldSong/moonlight-android)** | Yukino Song (ClassicOldSong) | GPL-3.0 | Pan/zoom, modos de toque, clipboard, comandos de servidor, Virtual Display, pacing Warp, Back como Meta, Force QWERTY e Back abre o menu (via Artemide). |
| **[Artemide](https://github.com/derflacco/moonlight-android)** | derflacco | GPL-3.0 | Fast V-Sync (NanoPacer), Sync/Async LFR, decodificação assíncrona e melhorias MediaTek. |
| **[Moonlight (Axixi2233 fork)](https://github.com/Axixi2233/moonlight-android)** | Axixi2233 | GPL-3.0 | Teclado virtual completo, formato de atalhos e modo Lite de estatísticas (via Artemis). |
| **LavArtemis** | Lavagnou | GPL-3.0 | Serviço de acessibilidade de teclado físico, hints ADPF, monitoramento térmico e percentis de pacing. |
| **[Moonlight V+](https://github.com/qiin2333/moonlight-vplus)** | qiin2333 & CLF (CLF184) | GPL-3.0 | Exportador USB/IP (`usbip-backend`), rotas Bluetooth para gatilhos DualSense, áudio háptico, 1% lows e bitrate em tempo real. |
| **[Moonlight X Turnip](https://github.com/NoCrypt/moonlight-x-turnip)** | ncpt (NoCrypt) | GPL-3.0 | Suporte a driver Vulkan customizado para PyroWave. |
| **[Moonlit Artemis](https://github.com/4had45/moonlit-artemis)** | Mohammad Forhad (4had45) | GPL-3.0 | Alternador de monitores e pinch-to-zoom contínuo. |
| **[Moonlight Fold](https://github.com/sblocktechnologies/moonlight-fold)** | sblocktechnologies | GPL-3.0 | Adaptação de transmissão para tela interna de aparelhos dobráveis. |
| **[Nightjar](https://github.com/RamazanKara/nightjar)** | Ramazan Kara | GPL-3.0 | Mapeamento de efeitos de gatilho DualSense do host para Java. |
| **[Apollo microphone](https://github.com/logabell/apollo-microphone)** | logabell | GPL-3.0 | Captura e protocolo de microfone in-stream de baixa latência. |
| **[VBAN and VB-Cable](https://vb-audio.com/Cable/)** | VB-Audio (Vincent Burel) | Donationware | Formato de transporte VBAN e microfone virtual VB-Cable no Windows. |
| **[Moonlight Common C](https://github.com/moonlight-stream/moonlight-common-c)** | Cameron Gutman & Diego Waxemberg | GPL-3.0 | Biblioteca nativa em C de streaming, RTP e controle (com modificações do Starwire e do MiWire). |
| **[PyroWave](https://github.com/Themaister/pyrowave) & [libadrenotools](https://github.com/bylaws/libadrenotools)** | Themaister / bylaws | MIT / BSD-2 | Codec PyroWave e carregador de drivers Vulkan customizados. |
| **[usbipdcpp](https://github.com/yunsmall/usbipdcpp) & [usbip-win2](https://github.com/vadimgrn/usbip-win2)** | yunsmall / vadimgrn | LGPL-3.0 / BSD-2 | Servidor USB/IP nativo e protocolo do driver Windows. |
| **nanors & enet** | sleepybishop / cgutman | MIT | Reed-Solomon FEC e transporte UDP confiável. |
| **[Vibepollo](https://github.com/Nonary/Vibepollo) / [Apollo](https://github.com/ClassicOldSong/Apollo) / [Sunshine](https://github.com/LizardByte/Sunshine)** | Nonary / ClassicOldSong / LizardByte | GPL-3.0 | Servidores host compatíveis no PC. |
| **[Claude Code](https://claude.com/claude-code) & [REA](https://github.com/morluto/rea)** | Anthropic / morluto | — | Desenvolvimento original no Starwire, testes e engenharia reversa / análise. |

### Isenção de Marcas Registradas (Trademark Disclaimer)
* *Xiaomi*, *Mi Box*, *Android*, *Google TV*, *NVIDIA*, *GeForce*, *PlayStation*, *DualSense* e outros nomes citados são marcas comerciais de seus respectivos proprietários. O uso destes nomes destina-se exclusivamente a identificar e descrever compatibilidade técnica de hardware e software, sem qualquer afiliação, endosso ou patrocínio.
