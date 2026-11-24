# 🎮 MiWire

<div align="center">

<img src="docs/images/title.svg" alt="MiWire" width="380" />

**Cliente de streaming Moonlight hiper-calibrado para a Xiaomi Mi Box S (3ª Geração) a 1080p @ 60 Hz.**

*Fork especializado do [Starwire](https://github.com/SicarioGaming/Starwire) (por SicarioGaming) e do [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) (por Cameron Gutman).*

[![Licença: GPL-3.0](https://img.shields.io/badge/licen%C3%A7a-GPL--3.0-FF6700?style=flat-square)](LICENSE.txt)
[![Android TV 11+](https://img.shields.io/badge/Android%20TV-11%2B%20(Google%20TV)-34A853?style=flat-square&logo=android&logoColor=white)](https://github.com/Gustavo14832/MiWire/releases/latest)
[![Target: Mi Box S Gen 3](https://img.shields.io/badge/Target-Mi%20Box%20S%20Gen%203-FF6700?style=flat-square)](https://github.com/Gustavo14832/MiWire/releases/latest)
[![Package: com.miware](https://img.shields.io/badge/package-com.miware-blue?style=flat-square)](https://github.com/Gustavo14832/MiWire/releases/latest)

### [⬇ Baixar MiWire.apk (GitHub Releases)](https://github.com/Gustavo14832/MiWire/releases/latest) · [📦 Cópia Direta no Repositório](release/MiWire.apk)

**Versão:** `0.3.0` (`v0.3.0-miware`) | **Tamanho:** `~14.6 MB` | **ID do Pacote:** `com.miware`  
**SHA-256:** `572CEBCEB5D47D410091CDBF2088762E2AE523C93A3D7DE0D5D42F7F1CF975FF`

</div>

---

> [!CAUTION]
> ### ⚠️ AVISO DE COMPATIBILIDADE ESPECÍFICA (LEIA ANTES DE INSTALAR)
> Este aplicativo é uma versão **especializada e altamente calibrada** projetada exclusivamente para a **Xiaomi Mi Box S de 3ª Geração (SoC Amlogic S905X5M, Android TV / Google TV)** operando em telas **1080p a 60 Hz**.
>
> As otimizações de baixo nível presentes neste APK — como o controle de bypass do Hardware Composer da Amlogic, a sincronização de fase de VSync cravada em 16.66 ms, o acumulador sub-pixel de mouse para Android TV e a trava de modo de desempenho contínuo — foram criadas especificamente para contornar limitações e comportamentos do driver da Amlogic.
>
> **Em celulares, tablets ou aparelhos com telas de 90 Hz, 120 Hz ou VRR, este APK se comportará de forma inadequada (podendo causar engasgos e perda de sincronia).** Para outros aparelhos, utilize o [Starwire original](https://github.com/SicarioGaming/Starwire) ou o [Moonlight oficial](https://github.com/moonlight-stream/moonlight-android).

---

## 🌟 O que é o MiWire?

O **MiWire** é um fork independente derivado do projeto original **[Starwire](https://github.com/SicarioGaming/Starwire)** (criado por *SicarioGaming*), construído sobre o **[Moonlight Android](https://github.com/moonlight-stream/moonlight-android)** (criado por *Cameron Gutman*) e reunindo recursos de outros clientes da comunidade Moonlight.

Ele nasceu com um propósito específico: transformar a **Xiaomi Mi Box S Gen 3** em um console de streaming de baixíssima latência para jogos de PC na rede local ou na nuvem (como instâncias GPU usando **Sunshine**, **Vibepollo** ou **Apollo**).

---

## 🚀 Recursos Exclusivos do MiWire (Otimizações Mi Box S Gen 3)

### 1. 👻 Ghost Overlay (Modo Fantasma de Composição 60 Hz Contínua) + Keep-Alive
* **O Problema Resolvido:** No SoC Amlogic da Mi Box, quando nenhum elemento de UI é desenhado na tela, o *Hardware Composer* ativa um modo de economia agressivo (*Bypass*) e o governador de CPU reduz o clock, quebrando a sincronia com o relógio do Vulkan e causando **83+ pulos de quadro (skips)** por sessão.
* **A Solução do MiWire:** Quando as estatísticas estão ocultas na TV, o MiWire ativa o **Ghost Overlay** (sobreposição invisível com alfa `0x01000000`, equivalente a ~0,39% de opacidade) aliado a um pulso periódico de 1 Hz (`uiKeepAliveView`). Isso mantém o *SurfaceFlinger* em composição contínua a 60.00 Hz sem obstruir a imagem do jogo.

### 2. ⏱️ Frame Pacing `mibox-60hz` & Renderizador Vulkan Avançado
* **Pacing dedicado (`FRAME_PACING_MIBOX_60HZ`):** Sincronização *phase-locked* em 60 Hz com alvo de **1 vsync/frame**, janela de jitter de 2,5 s (99% de cobertura), decaimento rápido após pausas estáticas e limite de buffer reduzido para **7 ms**.
* **Nitidez Vulkan RCAS + 10-bit SDR:** Filtro AMD FidelityFX Contrast-Adaptive Sharpening (`Off`, `Balanced`, `Crisp`) via shader GLSL/SPIR-V com gate de ruído, reconstrução explícita de croma e *Spatial Dithering* em swapchain 10-bit SDR.
* **Telemetria `p50/p95/p99`:** Estatísticas em tempo real de intervalos de apresentação no overlay (filtrando pausas ociosas do host).

### 3. 🎯 Calibração Automática Out-of-the-Box para Android TV
Na primeira execução em dispositivos Android TV / Amlogic (`isTvOrAmlogicDevice`), o MiWire configura automaticamente:
* **Vídeo:** `1920x1080` @ `60 FPS`, `35 Mbps`, **HEVC (H.265)**, renderizador **Vulkan**, `mibox-60hz`, dither `high`, RCAS `balanced`, Full Range (`0-255`), `exactSlices` desativado (preservando predição NVENC de quadro inteiro).
* **Decodificador Amlogic Codec2 (`c2.amlogic.*`):** Ignora a chave genérica `low-latency` (que causa travamentos no driver da Amlogic), aplica extensões de baixa latência da fabricante (`vendor.amlogic.*`, `vendor.media.omx.gamemode`, `operating-rate 240`, prioridade `0`), restaura HEVC RFI no `c2.amlogic`, expande buffers dinâmicos de entrada (>= 2 MB) e adiciona *watchdog* de 3 estágios contra travamento (IDR -> Flush -> Restart).
* **Sistema & Áudio:** Modo de Desempenho Sustentado (`setSustainedPerformanceMode`), ADPF (`PerformanceHintManager` em Java e C++), prioridade elevada de threads de renderização/áudio, `AudioTrack` de baixa latência no HDMI em Android 12+, fila máxima de áudio de `20 ms`, e skip de Wi-Fi lock quando conectado via cabo Ethernet.
* **Interface & Controles na TV:** Tema visual *Xiaomi Gaming* (carbono escuro `#0D0F12` com destaque laranja `#FF6700`), abertura de menu por `LB+RB`, botão *Back* como tecla Meta (`Win`), deadzone analógica mínima de `3%` (e gatilhos a `2%`), desativação de controles por toque, botão flutuante e sensores de movimento.

### 4. 🖱️ Mouse Anti-Coasting (1000 Hz) & Periféricos
* **Acumulador Sub-pixel:** Deltas fracionários de mouse relativos são acumulados em vez de descartados, eliminando travamentos no início de movimentos lentos de câmera.
* **Engine C (`moonlight-common-c`) Tunada:** Coalescência de mouse a 1 ms (`1000 Hz`) com envio imediato de cliques/teclas urgentes, drenagem de pacotes UDP em rajada via `recvmmsg`, marcação QoS DSCP (EF áudio / CS4 vídeo) e `PR_SET_TIMERSLACK = 1 ns`.
* **Controles e Teclado:** Deduplicação de pacotes repetidos de controle (com heartbeat de 1 s), calibração de centro de analógico (`StickCenterStore`), serviço de acessibilidade para teclado físico (Alt+Tab, Win), suporte a teclas extras **ABNT2/ISO** e correção de `Print Screen`.

---

## 🛠️ Recursos Herdados do Ecossistema Starwire & Moonlight

Além das otimizações exclusivas para Mi Box S Gen 3, o MiWire preserva os recursos modernos trazidos pelo **Starwire** e demais forks:

* **ID de Pacote Próprio (`com.miware` / `com.miware.debug`):** Instala lado a lado com Moonlight oficial e Starwire sem conflito de assinatura.
* **Gatilhos Adaptativos DualSense (Experimental):** Efeitos de gatilho via Bluetooth (Android 12+) com suporte a hosts compatíveis (como Vibepollo) ou presets integrados.
* **Passthrough USB Completo (Experimental):** Compartilhe volantes, controles e dispositivos USB com o PC via túnel USB/IP criptografado e auxiliar Windows ([guia Windows USB](windows/USB-HELPER.md)).
* **Microfone Criptografado para Qualquer Host:** Envie seu microfone via protocolo criptografado SWMC v1 para o receptor Windows ([guia Microfone Windows](windows/README.md)), VBAN ou diretamente embutido no stream (Apollo).
* **Menu In-Stream Completo:** Modo toque/trackpad/multi-touch, teclado completo de 82 teclas na tela, atalhos importáveis em JSON, alternador de monitores, ajuste de bitrate em tempo real, sincronização de área de transferência, Virtual Display e comandos de servidor.

---

## 📥 Como Instalar e Usar na Mi Box S Gen 3

1. Baixe o **`MiWire.apk`** na página de [Releases do GitHub](https://github.com/Gustavo14832/MiWire/releases/latest) (ou diretamente em [`release/MiWire.apk`](release/MiWire.apk)).
2. Verifique a integridade (opcional): SHA-256 `572CEBCEB5D47D410091CDBF2088762E2AE523C93A3D7DE0D5D42F7F1CF975FF`.
3. Transfira o APK para a Mi Box via pendrive ou *Send Files to TV* e instale usando um gerenciador de arquivos (como *X-plore* ou *AnExplorer*).
4. Abra o **MiWire**, pareie com seu PC (**Sunshine**, **Vibepollo** ou **Apollo**) e jogue com 60 Hz cravados.
5. *(Opcional)* Para usar microfone ou passthrough USB com um PC Windows, confira os scripts e instruções na pasta [`windows/`](windows/README.md).

---

## 📚 Documentação e Repositório

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
| **Starwire** *(APK original do fork)* | [SicarioGaming](https://github.com/SicarioGaming/Starwire) | GPL-3.0 | Base direta do fork, interface Cover Glow, microfone criptografado (SWMC v1), passthrough USB/IP, DualSense Bluetooth, pipeline de testes e infraestrutura CI. |
| **Moonlight Android** | [Cameron Gutman (cgutman)](https://github.com/moonlight-stream/moonlight-android) | GPL-3.0 | Cliente Moonlight Android fundamental, arquitetura de streaming, áudio, input e decodificação. |
| **Moonlight X** | [MoreOrLessSoftware](https://github.com/MoreOrLessSoftware/moonlight-android) | GPL-3.0 | Base upstream do Starwire: renderizador Vulkan, PyroWave, menu overlay, configurações por jogo, Quick Launch e resoluções/FPS customizados. |
| **Artemis** | [Yukino Song (ClassicOldSong)](https://github.com/ClassicOldSong/moonlight-android) | GPL-3.0 | Pan/zoom, modos de toque, clipboard, comandos de servidor, Virtual Display, pacing Warp, Back como Meta e Force QWERTY. |
| **Artemide** | [derflacco](https://github.com/derflacco/moonlight-android) | GPL-3.0 | Fast V-Sync (NanoPacer), Sync/Async LFR, decodificação assíncrona e melhorias MediaTek. |
| **LavArtemis** | Lavagnou | GPL-3.0 | Modo Lite de estatísticas, acessibilidade de teclado físico, hints ADPF, monitoramento térmico e percentis de pacing. |
| **Moonlight-VPlus** | [qiin2333](https://github.com/qiin2333/moonlight-vplus) | GPL-3.0 | Exportador USB/IP (`usbip-backend`, com CLF184), rotas Bluetooth para gatilhos DualSense, áudio háptico e bitrate em tempo real. |
| **Moonlight X Turnip** | [ncpt (NoCrypt)](https://github.com/NoCrypt/moonlight-x-turnip) | GPL-3.0 | Suporte a driver Vulkan customizado para PyroWave. |
| **Moonlit Artemis** | [Mohammad Forhad (4had45)](https://github.com/4had45/moonlit-artemis) | GPL-3.0 | Alternador de monitores e pinch-to-zoom contínuo. |
| **Nightjar** | [Ramazan Kara](https://github.com/RamazanKara/nightjar) | GPL-3.0 | Mapeamento de efeitos de gatilho DualSense do host para Java. |
| **Apollo Microphone** | [logabell](https://github.com/logabell/apollo-microphone) | GPL-3.0 | Captura e protocolo de microfone in-stream de baixa latência. |
| **Moonlight Common C** | [Cameron Gutman & Diego Waxemberg](https://github.com/moonlight-stream/moonlight-common-c) | GPL-3.0 | Biblioteca nativa em C de streaming, RTP e controle (com modificações do Starwire e do MiWire). |
| **PyroWave & libadrenotools** | [Themaister](https://github.com/Themaister/pyrowave) / [bylaws](https://github.com/bylaws/libadrenotools) | MIT / BSD-2 | Codec PyroWave e carregador de drivers Vulkan customizados. |
| **usbipdcpp & usbip-win2** | [yunsmall](https://github.com/yunsmall/usbipdcpp) / [vadimgrn](https://github.com/vadimgrn/usbip-win2) | LGPL-3.0 / BSD-2 | Servidor USB/IP nativo e protocolo do driver Windows. |
| **nanors & enet** | [sleepybishop](https://github.com/sleepybishop/nanors) / [cgutman](https://github.com/cgutman/enet) | MIT | Reed-Solomon FEC e transporte UDP confiável. |
| **Vibepollo / Apollo / Sunshine** | [Nonary](https://github.com/Nonary/Vibepollo) / [ClassicOldSong](https://github.com/ClassicOldSong/Apollo) / [LizardByte](https://github.com/LizardByte/Sunshine) | GPL-3.0 | Servidores host compatíveis no PC. |

### Isenção de Marcas Registradas (Trademark Disclaimer)
* *Xiaomi*, *Mi Box*, *Android*, *Google TV*, *NVIDIA*, *GeForce*, *PlayStation*, *DualSense* e outros nomes citados são marcas comerciais de seus respectivos proprietários. O uso destes nomes destina-se exclusivamente a identificar e descrever compatibilidade técnica de hardware e software, sem qualquer afiliação, endosso ou patrocínio.
